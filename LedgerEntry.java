package com.wallet.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ledger entry for double-entry bookkeeping
 * Every transfer creates exactly 2 entries: 1 DEBIT and 1 CREDIT
 */
@Entity
@Table(name = "ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "wallet_id", nullable = false)
    private String walletId;

    @Column(name = "transfer_id", nullable = false)
    private String transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

