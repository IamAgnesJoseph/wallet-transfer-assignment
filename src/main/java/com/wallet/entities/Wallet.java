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
 * Wallet entity representing a user's wallet with balance tracking
 * Uses optimistic locking with @Version to handle concurrent updates
 */
@Entity
@Table(name = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Debit amount from wallet
     * @param amount amount to debit
     * @throws IllegalArgumentException if insufficient balance
     */
    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in wallet: " + this.id);
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Credit amount to wallet
     * @param amount amount to credit
     */
    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}

