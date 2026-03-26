package com.wallet.exceptions;

/**
 * Exception thrown when a wallet has insufficient balance for a transfer
 */
public class InsufficientBalanceException extends RuntimeException {
    
    public InsufficientBalanceException(String walletId) {
        super("Insufficient balance in wallet: " + walletId);
    }
}

