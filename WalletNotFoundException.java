package com.wallet.exceptions;

/**
 * Exception thrown when a wallet is not found
 */
public class WalletNotFoundException extends RuntimeException {
    
    public WalletNotFoundException(String walletId) {
        super("Wallet not found: " + walletId);
    }
}

