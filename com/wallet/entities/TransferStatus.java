package com.wallet.entities;

/**
 * Transfer status enum representing the state machine
 * PENDING -> PROCESSED (success)
 * PENDING -> FAILED (failure)
 */
public enum TransferStatus {
    PENDING,
    PROCESSED,
    FAILED
}

