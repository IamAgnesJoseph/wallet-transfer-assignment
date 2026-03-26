package com.wallet.repositories;

import com.wallet.entities.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Wallet entity
 * Uses pessimistic locking for concurrent transfer safety
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    /**
     * Find wallet by ID with pessimistic write lock
     * This ensures that concurrent transactions cannot modify the same wallet simultaneously
     * 
     * @param id wallet ID
     * @return Optional containing the locked wallet
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") String id);
}

