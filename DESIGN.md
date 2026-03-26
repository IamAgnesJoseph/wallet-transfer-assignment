# Wallet Transfer Service - Design Documentation

## Overview

This document explains the design decisions, architecture, and implementation strategies for the wallet transfer service.

## Architecture

The implementation follows a **clean layered architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────┐
│         Controller Layer                │  - Request validation
│      (TransferController)               │  - HTTP mapping
└─────────────────────────────────────────┘  - Thin handlers
                  ↓
┌─────────────────────────────────────────┐
│          Service Layer                  │  - Business logic
│      (TransferServiceImpl)              │  - Orchestration
└─────────────────────────────────────────┘  - Idempotency
                  ↓                           - Transaction management
┌─────────────────────────────────────────┐
│        Repository Layer                 │  - Database operations
│  (WalletRepository, etc.)               │  - Query methods
└─────────────────────────────────────────┘  - Locking strategies
                  ↓
┌─────────────────────────────────────────┐
│         Domain Models                   │  - Entities
│  (Wallet, Transfer, etc.)               │  - State transitions
└─────────────────────────────────────────┘  - Validation rules
```

## Database Schema Design

### Tables

#### 1. `wallets`
Stores wallet information and balances.

```sql
CREATE TABLE wallets (
    id VARCHAR(255) PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,  -- Optimistic locking
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);
```

**Key Design Decisions:**
- `version` column for optimistic locking (backup to pessimistic locks)
- `CHECK` constraint ensures balance never goes negative
- Indexed on `id` for fast lookups

#### 2. `transfers`
Stores transfer records with state machine.

```sql
CREATE TABLE transfers (
    id VARCHAR(255) PRIMARY KEY,
    from_wallet_id VARCHAR(255) NOT NULL,
    to_wallet_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,  -- PENDING, PROCESSED, FAILED
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_from_wallet FOREIGN KEY (from_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_to_wallet FOREIGN KEY (to_wallet_id) REFERENCES wallets(id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_different_wallets CHECK (from_wallet_id != to_wallet_id),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);
```

**Key Design Decisions:**
- Foreign keys ensure referential integrity
- `CHECK` constraints prevent invalid transfers (same wallet, negative amounts)
- Status enum enforced at database level
- Indexed on wallet IDs and status for efficient queries

#### 3. `ledger_entries`
Double-entry bookkeeping ledger.

```sql
CREATE TABLE ledger_entries (
    id VARCHAR(255) PRIMARY KEY,
    wallet_id VARCHAR(255) NOT NULL,
    transfer_id VARCHAR(255) NOT NULL,
    entry_type VARCHAR(50) NOT NULL,  -- DEBIT, CREDIT
    amount DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id),
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);
```

**Key Design Decisions:**
- Every transfer creates exactly 2 entries (1 DEBIT + 1 CREDIT)
- Immutable audit trail
- Indexed on wallet_id and transfer_id for queries

#### 4. `idempotency_records`
Ensures exactly-once semantics.

```sql
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(255) PRIMARY KEY,  -- Unique constraint
    transfer_id VARCHAR(255) NOT NULL,
    request_payload TEXT NOT NULL,
    response_payload TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_idempotency_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);
```

**Key Design Decisions:**
- `UNIQUE` constraint on idempotency_key prevents duplicates at database level
- Stores request/response for exact replay
- Created atomically with transfer in same transaction

## Idempotency Strategy

### Problem
Distributed systems may experience:
- Duplicate requests (client retries)
- Network failures after processing but before response
- Load balancer retries

### Solution
**Database-backed idempotency with unique constraints**

### Implementation

1. **Idempotency Key Storage**
   - Client provides `idempotencyKey` in request
   - Stored in `idempotency_records` table with UNIQUE constraint
   - Database enforces uniqueness, preventing race conditions

2. **Duplicate Detection**
   ```java
   // Check if request already processed
   Optional<IdempotencyRecord> existing = 
       idempotencyRecordRepository.findByIdempotencyKey(key);
   
   if (existing.isPresent()) {
       return getExistingResponse(existing.get());
   }
   ```

3. **Response Replay**
   - Original response stored as JSON in `response_payload`
   - Duplicate requests return exact same response
   - No side effects executed twice

4. **Race Condition Handling**
   ```java
   try {
       idempotencyRecordRepository.save(record);
   } catch (DataIntegrityViolationException e) {
       // Another thread won the race
       return getExistingResponse();
   }
   ```

### Guarantees
- ✅ Exactly-once execution per idempotency key
- ✅ Same response for duplicate requests
- ✅ No duplicate transfers
- ✅ Safe under concurrent requests with same key

## Concurrency Strategy

### Problem
Multiple transfers may attempt to debit the same wallet simultaneously, leading to:
- Race conditions
- Double spending
- Incorrect balances
- Lost updates

### Solution
**Multi-layered concurrency control**

### Layer 1: Transaction Isolation
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
```
- Highest isolation level
- Prevents phantom reads and non-repeatable reads
- Ensures complete transaction isolation

### Layer 2: Pessimistic Locking
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdWithLock(@Param("id") String id);
```
- `SELECT FOR UPDATE` at database level
- Locks wallet rows during transaction
- Other transactions wait until lock released

### Layer 3: Optimistic Locking (Backup)
```java
@Version
private Long version;
```
- JPA version field
- Detects concurrent modifications
- Throws `OptimisticLockingFailureException` if conflict

### Layer 4: Deadlock Prevention
```java
// Always lock wallets in alphabetical order
String firstWalletId = fromWalletId.compareTo(toWalletId) < 0 
    ? fromWalletId : toWalletId;
String secondWalletId = ...;

// Acquire locks in consistent order
Wallet first = walletRepository.findByIdWithLock(firstWalletId);
Wallet second = walletRepository.findByIdWithLock(secondWalletId);
```
- Prevents circular wait conditions
- Ensures deterministic lock ordering

### Guarantees
- ✅ No double spending
- ✅ Correct balances under concurrent load
- ✅ No race conditions
- ✅ Atomic wallet updates

## Double-Entry Ledger

### Principle
Every financial transaction has equal and opposite effects in at least two accounts.

### Implementation
```java
// DEBIT from source wallet
LedgerEntry debit = LedgerEntry.builder()
    .walletId(fromWalletId)
    .transferId(transferId)
    .entryType(EntryType.DEBIT)
    .amount(amount)
    .build();

// CREDIT to destination wallet
LedgerEntry credit = LedgerEntry.builder()
    .walletId(toWalletId)
    .transferId(transferId)
    .entryType(EntryType.CREDIT)
    .amount(amount)
    .build();
```

### Invariants
1. Every transfer creates exactly 2 ledger entries
2. Sum of all ledger entries = 0 (balanced ledger)
3. DEBIT amount = CREDIT amount
4. Ledger is immutable (audit trail)

### Verification
```sql
-- Ledger should always balance
SELECT SUM(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) 
FROM ledger_entries;
-- Result should be 0
```

## State Machine

### Transfer States
```
PENDING ──────> PROCESSED (success)
   │
   └──────────> FAILED (error)
```

### State Transitions
```java
public void markAsProcessed() {
    if (this.status != TransferStatus.PENDING) {
        throw new IllegalStateException("Invalid state transition");
    }
    this.status = TransferStatus.PROCESSED;
}
```

### Guarantees
- ✅ Only valid transitions allowed
- ✅ State changes are atomic
- ✅ Failed transfers don't affect balances
- ✅ Retry-safe (idempotency prevents re-execution)

## Transaction Boundaries

### Single Atomic Transaction
All operations in one database transaction:
1. Create idempotency record
2. Create transfer (PENDING)
3. Lock wallets
4. Update balances
5. Create ledger entries
6. Update transfer status (PROCESSED/FAILED)
7. Update idempotency record with response

### Rollback Behavior
If any step fails:
- Entire transaction rolls back
- No partial state
- Idempotency record may exist (prevents retry)
- Transfer marked as FAILED

## Error Handling

### Exception Hierarchy
- `WalletNotFoundException` → 404 Not Found
- `InsufficientBalanceException` → 400 Bad Request
- `InvalidTransferException` → 400 Bad Request
- `OptimisticLockingFailureException` → 409 Conflict

### Failure Scenarios
1. **Insufficient Balance**: Transfer marked FAILED, no balance change
2. **Wallet Not Found**: Transaction rolled back
3. **Concurrent Modification**: Client receives 409, should retry
4. **Duplicate Request**: Returns original response (200 OK)

## Testing Strategy

### Unit Tests
- Service layer logic
- State transitions
- Validation rules
- Idempotency behavior

### Integration Tests
- Full stack (Controller → Database)
- Idempotency verification
- Concurrent transfer safety
- Ledger correctness
- Balance integrity

### Concurrency Tests
- Multiple threads transferring from same wallet
- Verifies no double-spending
- Validates final balances

## Assumptions and Tradeoffs

### Assumptions
1. PostgreSQL database (or H2 for testing)
2. Single database instance (no distributed transactions)
3. Wallets pre-exist (no wallet creation in this service)
4. Idempotency keys are unique per client
5. Reasonable transaction volume (not high-frequency trading)

### Tradeoffs
1. **SERIALIZABLE isolation** → Higher safety, lower throughput
2. **Pessimistic locking** → Prevents conflicts, may cause contention
3. **Stored response payloads** → Exact replay, increased storage
4. **Alphabetical lock ordering** → Prevents deadlocks, adds complexity

## Future Enhancements
- Wallet balance API (GET /wallets/{id}/balance)
- Transfer history API (GET /transfers?walletId=...)
- Metrics and observability (Prometheus, Grafana)
- Distributed tracing (OpenTelemetry)
- Event sourcing for audit trail
- Asynchronous processing for high volume

