package com.wallet.repositories;

import com.wallet.entities.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LedgerEntry entity
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    /**
     * Find all ledger entries for a specific wallet
     * 
     * @param walletId wallet ID
     * @return list of ledger entries for the wallet
     */
    List<LedgerEntry> findByWalletId(String walletId);

    /**
     * Find all ledger entries for a specific transfer
     * 
     * @param transferId transfer ID
     * @return list of ledger entries for the transfer (should be exactly 2)
     */
    List<LedgerEntry> findByTransferId(String transferId);
}

