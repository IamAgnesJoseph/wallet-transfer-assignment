package com.wallet.repositories;

import com.wallet.entities.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for IdempotencyRecord entity
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Find idempotency record by idempotency key
     * 
     * @param idempotencyKey unique idempotency key
     * @return Optional containing the idempotency record if found
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}

