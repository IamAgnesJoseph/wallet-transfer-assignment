package com.wallet.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transfer entity representing a wallet-to-wallet transfer
 */
@Entity
@Table(name = "transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "from_wallet_id", nullable = false)
    private String fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private String toWalletId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TransferStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Mark transfer as processed
     */
    public void markAsProcessed() {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot mark transfer as PROCESSED from status: " + this.status);
        }
        this.status = TransferStatus.PROCESSED;
    }

    /**
     * Mark transfer as failed
     */
    public void markAsFailed() {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Cannot mark transfer as FAILED from status: " + this.status);
        }
        this.status = TransferStatus.FAILED;
    }
}

