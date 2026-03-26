package com.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.WalletTransferApplication;
import com.wallet.entities.TransferStatus;
import com.wallet.entities.Wallet;
import com.wallet.models.request.TransferRequest;
import com.wallet.models.response.TransferResponse;
import com.wallet.repositories.IdempotencyRecordRepository;
import com.wallet.repositories.LedgerEntryRepository;
import com.wallet.repositories.TransferRepository;
import com.wallet.repositories.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for wallet transfer functionality
 * Tests the full stack including database transactions
 */
@SpringBootTest(classes = WalletTransferApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void setUp() {
        // Clean up database
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        walletRepository.deleteAll();

        // Create test wallets
        Wallet wallet1 = Wallet.builder()
                .id("wallet_1")
                .balance(new BigDecimal("1000.00"))
                .version(0L)
                .build();

        Wallet wallet2 = Wallet.builder()
                .id("wallet_2")
                .balance(new BigDecimal("500.00"))
                .version(0L)
                .build();

        walletRepository.save(wallet1);
        walletRepository.save(wallet2);
    }

    @Test
    void testCreateTransfer_Success() throws Exception {
        // Arrange
        TransferRequest request = TransferRequest.builder()
                .idempotencyKey("test-key-1")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .build();

        // Act & Assert
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromWalletId").value("wallet_1"))
                .andExpect(jsonPath("$.toWalletId").value("wallet_2"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify wallet balances
        Wallet wallet1 = walletRepository.findById("wallet_1").orElseThrow();
        Wallet wallet2 = walletRepository.findById("wallet_2").orElseThrow();
        assertEquals(new BigDecimal("900.00"), wallet1.getBalance());
        assertEquals(new BigDecimal("600.00"), wallet2.getBalance());

        // Verify ledger entries
        assertEquals(2, ledgerEntryRepository.count());
    }

    @Test
    void testCreateTransfer_Idempotency() throws Exception {
        // Arrange
        TransferRequest request = TransferRequest.builder()
                .idempotencyKey("test-key-2")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .build();

        // Act - First request
        MvcResult result1 = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(), TransferResponse.class);

        // Act - Second request with same idempotency key
        MvcResult result2 = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()) // Should return 200 for duplicate
                .andReturn();

        TransferResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(), TransferResponse.class);

        // Assert - Both responses should be identical
        assertEquals(response1.getTransferId(), response2.getTransferId());
        assertEquals(response1.getStatus(), response2.getStatus());

        // Verify only one transfer was created
        assertEquals(1, transferRepository.count());

        // Verify wallet balances (should only be debited/credited once)
        Wallet wallet1 = walletRepository.findById("wallet_1").orElseThrow();
        Wallet wallet2 = walletRepository.findById("wallet_2").orElseThrow();
        assertEquals(new BigDecimal("900.00"), wallet1.getBalance());
        assertEquals(new BigDecimal("600.00"), wallet2.getBalance());
    }

    @Test
    void testCreateTransfer_InsufficientBalance() throws Exception {
        // Arrange
        TransferRequest request = TransferRequest.builder()
                .idempotencyKey("test-key-3")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("2000.00")) // More than available
                .build();

        // Act & Assert
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient balance in wallet: wallet_1"));

        // Verify wallet balances unchanged
        Wallet wallet1 = walletRepository.findById("wallet_1").orElseThrow();
        assertEquals(new BigDecimal("1000.00"), wallet1.getBalance());
    }

    @Test
    void testCreateTransfer_ConcurrentTransfers() throws Exception {
        // This test verifies that concurrent transfers from the same wallet
        // are handled safely without double-spending

        int numberOfThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Submit 5 concurrent transfers of 250 each (total 1250, but wallet has only 1000)
        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            Future<Boolean> future = executorService.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // Wait for all threads to be ready

                    TransferRequest request = TransferRequest.builder()
                            .idempotencyKey("concurrent-key-" + index)
                            .fromWalletId("wallet_1")
                            .toWalletId("wallet_2")
                            .amount(new BigDecimal("250.00"))
                            .build();

                    MvcResult result = mockMvc.perform(post("/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    return result.getResponse().getStatus() == 201;
                } catch (Exception e) {
                    return false;
                }
            });
            futures.add(future);
        }

        // Wait for all transfers to complete
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        // Assert - Count successful transfers
        long successfulTransfers = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(success -> success)
                .count();

        // Only 4 transfers should succeed (4 * 250 = 1000), the 5th should fail
        assertTrue(successfulTransfers <= 4, "Should have at most 4 successful transfers");

        // Verify final wallet balance
        Wallet wallet1 = walletRepository.findById("wallet_1").orElseThrow();
        assertTrue(wallet1.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Wallet balance should never go negative");

        // Balance should be 1000 - (successfulTransfers * 250)
        BigDecimal expectedBalance = new BigDecimal("1000.00")
                .subtract(new BigDecimal(successfulTransfers * 250));
        assertEquals(expectedBalance, wallet1.getBalance());
    }

    @Test
    void testCreateTransfer_DoubleLedgerEntry() throws Exception {
        // Arrange
        TransferRequest request = TransferRequest.builder()
                .idempotencyKey("test-key-4")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .build();

        // Act
        MvcResult result = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TransferResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransferResponse.class);

        // Assert - Verify exactly 2 ledger entries
        var ledgerEntries = ledgerEntryRepository.findByTransferId(response.getTransferId());
        assertEquals(2, ledgerEntries.size());

        // Verify one DEBIT and one CREDIT
        long debits = ledgerEntries.stream().filter(e -> e.getEntryType().name().equals("DEBIT")).count();
        long credits = ledgerEntries.stream().filter(e -> e.getEntryType().name().equals("CREDIT")).count();
        assertEquals(1, debits);
        assertEquals(1, credits);

        // Verify amounts match
        assertTrue(ledgerEntries.stream().allMatch(e ->
                e.getAmount().compareTo(new BigDecimal("100.00")) == 0));
    }
}

