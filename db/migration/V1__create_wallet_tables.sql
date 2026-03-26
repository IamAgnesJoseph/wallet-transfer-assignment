-- Create wallets table
CREATE TABLE wallets (
    id VARCHAR(255) PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- Create index on wallet id for faster lookups
CREATE INDEX idx_wallets_id ON wallets(id);

-- Create transfers table
CREATE TABLE transfers (
    id VARCHAR(255) PRIMARY KEY,
    from_wallet_id VARCHAR(255) NOT NULL,
    to_wallet_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_from_wallet FOREIGN KEY (from_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_to_wallet FOREIGN KEY (to_wallet_id) REFERENCES wallets(id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_different_wallets CHECK (from_wallet_id != to_wallet_id),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Create indexes on transfers
CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to_wallet ON transfers(to_wallet_id);
CREATE INDEX idx_transfers_status ON transfers(status);
CREATE INDEX idx_transfers_created_at ON transfers(created_at);

-- Create ledger_entries table for double-entry bookkeeping
CREATE TABLE ledger_entries (
    id VARCHAR(255) PRIMARY KEY,
    wallet_id VARCHAR(255) NOT NULL,
    transfer_id VARCHAR(255) NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id),
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

-- Create indexes on ledger_entries
CREATE INDEX idx_ledger_wallet ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_transfer ON ledger_entries(transfer_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);

-- Create idempotency_records table
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    transfer_id VARCHAR(255) NOT NULL,
    request_payload TEXT NOT NULL,
    response_payload TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id),
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Create index on idempotency_key for fast duplicate detection
CREATE UNIQUE INDEX idx_idempotency_key ON idempotency_records(idempotency_key);
CREATE INDEX idx_idempotency_transfer ON idempotency_records(transfer_id);

