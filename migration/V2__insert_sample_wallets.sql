-- Insert sample wallets for testing
INSERT INTO wallets (id, balance, version, created_at, updated_at) 
VALUES 
    ('wallet_1', 1000.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wallet_2', 500.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('wallet_3', 2000.00, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

