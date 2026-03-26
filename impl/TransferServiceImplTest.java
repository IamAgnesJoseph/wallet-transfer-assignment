package com.wallet.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.entities.*;
import com.wallet.exceptions.InsufficientBalanceException;
import com.wallet.exceptions.InvalidTransferException;
import com.wallet.exceptions.WalletNotFoundException;
import com.wallet.models.request.TransferRequest;
import com.wallet.models.response.TransferResponse;
import com.wallet.repositories.IdempotencyRecordRepository;
import com.wallet.repositories.LedgerEntryRepository;
import com.wallet.repositories.TransferRepository;
import com.wallet.repositories.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransferServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private TransferServiceImpl transferService;

    private Wallet fromWallet;
    private Wallet toWallet;
    private TransferRequest transferRequest;

    @BeforeEach
    void setUp() {
        fromWallet = Wallet.builder()
                .id("wallet_1")
                .balance(new BigDecimal("1000.00"))
                .version(0L)
                .build();

        toWallet = Wallet.builder()
                .id("wallet_2")
                .balance(new BigDecimal("500.00"))
                .version(0L)
                .build();

        transferRequest = TransferRequest.builder()
                .idempotencyKey("test-key-123")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void testCreateTransfer_Success() {
        // Arrange
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.findByIdWithLock("wallet_1")).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByIdWithLock("wallet_2")).thenReturn(Optional.of(toWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TransferResponse response = transferService.createTransfer(transferRequest);

        // Assert
        assertNotNull(response);
        assertEquals("wallet_1", response.getFromWalletId());
        assertEquals("wallet_2", response.getToWalletId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals(TransferStatus.PROCESSED, response.getStatus());

        // Verify wallet balances were updated
        assertEquals(new BigDecimal("900.00"), fromWallet.getBalance());
        assertEquals(new BigDecimal("600.00"), toWallet.getBalance());

        // Verify ledger entries were created
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    void testCreateTransfer_InsufficientBalance() {
        // Arrange
        transferRequest.setAmount(new BigDecimal("2000.00")); // More than available
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.findByIdWithLock("wallet_1")).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByIdWithLock("wallet_2")).thenReturn(Optional.of(toWallet));

        // Act & Assert
        assertThrows(InsufficientBalanceException.class, () -> {
            transferService.createTransfer(transferRequest);
        });

        // Verify transfer was marked as failed
        verify(transferRepository, times(2)).save(any(Transfer.class));
    }

    @Test
    void testCreateTransfer_WalletNotFound() {
        // Arrange
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.findByIdWithLock("wallet_1")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WalletNotFoundException.class, () -> {
            transferService.createTransfer(transferRequest);
        });
    }

    @Test
    void testCreateTransfer_SameWallet() {
        // Arrange
        transferRequest.setToWalletId("wallet_1"); // Same as fromWalletId

        // Act & Assert
        assertThrows(InvalidTransferException.class, () -> {
            transferService.createTransfer(transferRequest);
        });
    }

    @Test
    void testCreateTransfer_Idempotency_ReturnsSameResult() throws Exception {
        // Arrange - First request already processed
        Transfer existingTransfer = Transfer.builder()
                .id("transfer-123")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .status(TransferStatus.PROCESSED)
                .build();

        TransferResponse existingResponse = TransferResponse.builder()
                .transferId("transfer-123")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .status(TransferStatus.PROCESSED)
                .message("Transfer completed successfully")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String responsePayload = mapper.writeValueAsString(existingResponse);

        IdempotencyRecord existingRecord = IdempotencyRecord.builder()
                .idempotencyKey("test-key-123")
                .transferId("transfer-123")
                .requestPayload("{}")
                .responsePayload(responsePayload)
                .status(TransferStatus.PROCESSED)
                .build();

        when(idempotencyRecordRepository.findByIdempotencyKey("test-key-123"))
                .thenReturn(Optional.of(existingRecord));

        // Act
        TransferResponse response = transferService.createTransfer(transferRequest);

        // Assert
        assertNotNull(response);
        assertEquals("transfer-123", response.getTransferId());
        assertEquals(TransferStatus.PROCESSED, response.getStatus());

        // Verify no new transfer was created
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(walletRepository, never()).findByIdWithLock(anyString());
    }

    @Test
    void testCreateTransfer_NegativeAmount() {
        // Arrange
        transferRequest.setAmount(new BigDecimal("-100.00"));

        // Act & Assert
        assertThrows(InvalidTransferException.class, () -> {
            transferService.createTransfer(transferRequest);
        });
    }

    @Test
    void testCreateTransfer_ZeroAmount() {
        // Arrange
        transferRequest.setAmount(BigDecimal.ZERO);

        // Act & Assert
        assertThrows(InvalidTransferException.class, () -> {
            transferService.createTransfer(transferRequest);
        });
    }

    @Test
    void testGetTransfer_Success() {
        // Arrange
        Transfer transfer = Transfer.builder()
                .id("transfer-123")
                .fromWalletId("wallet_1")
                .toWalletId("wallet_2")
                .amount(new BigDecimal("100.00"))
                .status(TransferStatus.PROCESSED)
                .build();

        when(transferRepository.findById("transfer-123")).thenReturn(Optional.of(transfer));

        // Act
        TransferResponse response = transferService.getTransfer("transfer-123");

        // Assert
        assertNotNull(response);
        assertEquals("transfer-123", response.getTransferId());
        assertEquals(TransferStatus.PROCESSED, response.getStatus());
    }

    @Test
    void testGetTransfer_NotFound() {
        // Arrange
        when(transferRepository.findById("non-existent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(InvalidTransferException.class, () -> {
            transferService.getTransfer("non-existent");
        });
    }
}

