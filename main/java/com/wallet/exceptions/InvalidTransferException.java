package com.wallet.exceptions;

/**
 * Exception thrown when a transfer request is invalid
 */
public class InvalidTransferException extends RuntimeException {
    
    public InvalidTransferException(String message) {
        super(message);
    }
}

