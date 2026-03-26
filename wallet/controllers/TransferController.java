package com.wallet.controllers;

import com.wallet.models.request.TransferRequest;
import com.wallet.models.response.TransferResponse;
import com.wallet.services.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for wallet transfer operations
 * 
 * Responsibilities:
 * - Request validation
 * - HTTP request/response mapping
 * - Delegating business logic to service layer
 */
@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final TransferService transferService;

    /**
     * Create a new transfer
     * 
     * POST /transfers
     * 
     * Request body:
     * {
     *   "idempotencyKey": "abc123",
     *   "fromWalletId": "wallet_1",
     *   "toWalletId": "wallet_2",
     *   "amount": 100
     * }
     * 
     * @param request transfer request
     * @return transfer response with HTTP 201 Created
     */
    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody TransferRequest request) {
        log.info("Received transfer request: fromWallet={}, toWallet={}, amount={}, idempotencyKey={}", 
                request.getFromWalletId(), request.getToWalletId(), request.getAmount(), request.getIdempotencyKey());
        
        TransferResponse response = transferService.createTransfer(request);
        
        // Return 200 OK for duplicate requests (idempotent behavior)
        // Return 201 Created for new transfers
        HttpStatus status = response.getMessage() != null && response.getMessage().contains("Duplicate") 
                ? HttpStatus.OK : HttpStatus.CREATED;
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Get transfer by ID
     * 
     * GET /transfers/{transferId}
     * 
     * @param transferId transfer ID
     * @return transfer response
     */
    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable String transferId) {
        log.info("Fetching transfer: {}", transferId);
        TransferResponse response = transferService.getTransfer(transferId);
        return ResponseEntity.ok(response);
    }
}

