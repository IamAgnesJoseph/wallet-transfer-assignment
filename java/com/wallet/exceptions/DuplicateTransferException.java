package com.wallet.exceptions;

/**
 * Exception thrown when a duplicate idempotency key is detected
 * This is used internally to signal that the request is a duplicate
 */
public class DuplicateTransferException extends RuntimeException {
    
    private final String transferId;
    
    public DuplicateTransferException(String idempotencyKey, String transferId) {
        super("Duplicate transfer request with idempotency key: " + idempotencyKey);
        this.transferId = transferId;
    }
    
    public String getTransferId() {
        return transferId;
    }
}

