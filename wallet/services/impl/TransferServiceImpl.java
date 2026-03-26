package com.wallet.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.wallet.services.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of TransferService
 * 
 * Key Design Decisions:
 * 
 * 1. Idempotency Strategy:
 *    - Uses idempotency_records table with unique constraint on idempotency_key
 *    - Stores request and response payloads for exact replay
 *    - Returns original response for duplicate requests
 * 
 * 2. Concurrency Strategy:
 *    - Uses SERIALIZABLE isolation level for transfer transactions
 *    - Pessimistic write locks on wallet rows (SELECT FOR UPDATE)
 *    - Optimistic locking with @Version on Wallet entity as backup
 *    - Ensures no race conditions or double-spending
 * 
 * 3. Double-Entry Ledger:
 *    - Every transfer creates exactly 2 ledger entries
 *    - DEBIT entry for source wallet
 *    - CREDIT entry for destination wallet
 *    - Ledger always balances (sum of all entries = 0)
 * 
 * 4. Transaction Boundaries:
 *    - Entire transfer operation is atomic
 *    - All or nothing: wallet updates, ledger entries, and idempotency record
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferResponse createTransfer(TransferRequest request) {
        log.info("Processing transfer request with idempotency key: {}", request.getIdempotencyKey());

        // Step 1: Check for idempotency - return existing result if duplicate
        Optional<IdempotencyRecord> existingRecord = 
            idempotencyRecordRepository.findByIdempotencyKey(request.getIdempotencyKey());
        
        if (existingRecord.isPresent()) {
            log.info("Duplicate request detected for idempotency key: {}", request.getIdempotencyKey());
            return getExistingTransferResponse(existingRecord.get());
        }

        // Step 2: Validate request
        validateTransferRequest(request);

        // Step 3: Generate transfer ID
        String transferId = UUID.randomUUID().toString();

        // Step 4: Create idempotency record first (with unique constraint)
        // This prevents duplicate processing even if multiple requests arrive simultaneously
        IdempotencyRecord idempotencyRecord = createIdempotencyRecord(request, transferId);
        
        try {
            idempotencyRecordRepository.save(idempotencyRecord);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created the record first
            log.warn("Race condition detected for idempotency key: {}", request.getIdempotencyKey());
            Optional<IdempotencyRecord> raceRecord = 
                idempotencyRecordRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (raceRecord.isPresent()) {
                return getExistingTransferResponse(raceRecord.get());
            }
            throw new InvalidTransferException("Failed to process transfer due to concurrent request");
        }

        // Step 5: Create transfer entity
        Transfer transfer = createTransferEntity(request, transferId);
        transferRepository.save(transfer);

        try {
            // Step 6: Execute the transfer with pessimistic locking
            executeTransfer(transfer);

            // Step 7: Mark transfer as processed
            transfer.markAsProcessed();
            transferRepository.save(transfer);

            // Step 8: Update idempotency record with success
            TransferResponse response = buildTransferResponse(transfer, "Transfer completed successfully");
            updateIdempotencyRecord(idempotencyRecord, response, TransferStatus.PROCESSED);

            log.info("Transfer completed successfully: {}", transferId);
            return response;

        } catch (Exception e) {
            log.error("Transfer failed: {}", transferId, e);
            
            // Mark transfer as failed
            transfer.markAsFailed();
            transferRepository.save(transfer);

            // Update idempotency record with failure
            TransferResponse errorResponse = buildTransferResponse(transfer, "Transfer failed: " + e.getMessage());
            updateIdempotencyRecord(idempotencyRecord, errorResponse, TransferStatus.FAILED);

            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransferResponse getTransfer(String transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new InvalidTransferException("Transfer not found: " + transferId));
        return buildTransferResponse(transfer, null);
    }

    /**
     * Execute the actual transfer with pessimistic locking
     * This method acquires locks on both wallets to prevent concurrent modifications
     */
    private void executeTransfer(Transfer transfer) {
        // Lock wallets in a consistent order to prevent deadlocks
        // Always lock in alphabetical order of wallet IDs
        String firstWalletId = transfer.getFromWalletId().compareTo(transfer.getToWalletId()) < 0
            ? transfer.getFromWalletId() : transfer.getToWalletId();
        String secondWalletId = transfer.getFromWalletId().compareTo(transfer.getToWalletId()) < 0
            ? transfer.getToWalletId() : transfer.getFromWalletId();

        // Acquire locks in order
        Wallet firstWallet = walletRepository.findByIdWithLock(firstWalletId)
                .orElseThrow(() -> new WalletNotFoundException(firstWalletId));
        Wallet secondWallet = walletRepository.findByIdWithLock(secondWalletId)
                .orElseThrow(() -> new WalletNotFoundException(secondWalletId));

        // Identify source and destination
        Wallet fromWallet = transfer.getFromWalletId().equals(firstWalletId) ? firstWallet : secondWallet;
        Wallet toWallet = transfer.getToWalletId().equals(firstWalletId) ? firstWallet : secondWallet;

        // Debit from source wallet
        try {
            fromWallet.debit(transfer.getAmount());
        } catch (IllegalArgumentException e) {
            throw new InsufficientBalanceException(fromWallet.getId());
        }

        // Credit to destination wallet
        toWallet.credit(transfer.getAmount());

        // Save wallet updates
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Create double-entry ledger entries
        createLedgerEntries(transfer);
    }

    /**
     * Create double-entry ledger entries for the transfer
     */
    private void createLedgerEntries(Transfer transfer) {
        // DEBIT entry for source wallet
        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(transfer.getFromWalletId())
                .transferId(transfer.getId())
                .entryType(EntryType.DEBIT)
                .amount(transfer.getAmount())
                .build();

        // CREDIT entry for destination wallet
        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID().toString())
                .walletId(transfer.getToWalletId())
                .transferId(transfer.getId())
                .entryType(EntryType.CREDIT)
                .amount(transfer.getAmount())
                .build();

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        log.debug("Created ledger entries for transfer: {}", transfer.getId());
    }

    /**
     * Validate transfer request
     */
    private void validateTransferRequest(TransferRequest request) {
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidTransferException("Cannot transfer to the same wallet");
        }

        if (request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }
    }

    /**
     * Create transfer entity
     */
    private Transfer createTransferEntity(TransferRequest request, String transferId) {
        return Transfer.builder()
                .id(transferId)
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .amount(request.getAmount())
                .status(TransferStatus.PENDING)
                .build();
    }

    /**
     * Create idempotency record
     */
    private IdempotencyRecord createIdempotencyRecord(TransferRequest request, String transferId) {
        try {
            String requestPayload = objectMapper.writeValueAsString(request);
            return IdempotencyRecord.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .transferId(transferId)
                    .requestPayload(requestPayload)
                    .status(TransferStatus.PENDING)
                    .build();
        } catch (JsonProcessingException e) {
            throw new InvalidTransferException("Failed to serialize request: " + e.getMessage());
        }
    }

    /**
     * Update idempotency record with response
     */
    private void updateIdempotencyRecord(IdempotencyRecord record, TransferResponse response, TransferStatus status) {
        try {
            String responsePayload = objectMapper.writeValueAsString(response);
            record.setResponsePayload(responsePayload);
            record.setStatus(status);
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response", e);
        }
    }

    /**
     * Get existing transfer response from idempotency record
     */
    private TransferResponse getExistingTransferResponse(IdempotencyRecord record) {
        if (record.getResponsePayload() != null) {
            try {
                return objectMapper.readValue(record.getResponsePayload(), TransferResponse.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize response", e);
            }
        }

        // Fallback: build response from transfer
        Transfer transfer = transferRepository.findById(record.getTransferId())
                .orElseThrow(() -> new InvalidTransferException("Transfer not found: " + record.getTransferId()));
        return buildTransferResponse(transfer, "Duplicate request - returning original result");
    }

    /**
     * Build transfer response DTO
     */
    private TransferResponse buildTransferResponse(Transfer transfer, String message) {
        return TransferResponse.builder()
                .transferId(transfer.getId())
                .fromWalletId(transfer.getFromWalletId())
                .toWalletId(transfer.getToWalletId())
                .amount(transfer.getAmount())
                .status(transfer.getStatus())
                .createdAt(transfer.getCreatedAt())
                .message(message)
                .build();
    }
}

