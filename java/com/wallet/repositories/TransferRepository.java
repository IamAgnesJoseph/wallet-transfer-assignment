package com.wallet.repositories;

import com.wallet.entities.Transfer;
import com.wallet.entities.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Transfer entity
 */
@Repository
public interface TransferRepository extends JpaRepository<Transfer, String> {

    /**
     * Find all transfers by status
     * 
     * @param status transfer status
     * @return list of transfers with the given status
     */
    List<Transfer> findByStatus(TransferStatus status);

    /**
     * Find all transfers from a specific wallet
     * 
     * @param fromWalletId source wallet ID
     * @return list of transfers from the wallet
     */
    List<Transfer> findByFromWalletId(String fromWalletId);

    /**
     * Find all transfers to a specific wallet
     * 
     * @param toWalletId destination wallet ID
     * @return list of transfers to the wallet
     */
    List<Transfer> findByToWalletId(String toWalletId);
}

