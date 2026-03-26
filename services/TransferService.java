package com.wallet.services;

import com.wallet.models.request.TransferRequest;
import com.wallet.models.response.TransferResponse;

/**
 * Service interface for wallet transfer operations
 */
public interface TransferService {

    /**
     * Create a new transfer between wallets
     * Implements idempotency using the idempotency key
     * Ensures exactly-once semantics at the API level
     * 
     * @param request transfer request containing idempotency key, wallet IDs, and amount
     * @return transfer response with transfer details
     */
    TransferResponse createTransfer(TransferRequest request);

    /**
     * Get transfer by ID
     * 
     * @param transferId transfer ID
     * @return transfer response
     */
    TransferResponse getTransfer(String transferId);
}

