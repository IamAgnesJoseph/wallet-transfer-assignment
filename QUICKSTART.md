# Quick Start Guide

## Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL (or use Docker)

## 1. Start PostgreSQL with Docker

```bash
docker run --name postgres-wallet \
  -e POSTGRES_DB=wallet_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:15
```

## 2. Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

Application starts at: `http://localhost:8080`

## 3. Test the API

### Create a Transfer

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-123",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 100.00
  }'
```

**Expected Response:**
```json
{
  "transferId": "550e8400-e29b-41d4-a716-446655440000",
  "fromWalletId": "wallet_1",
  "toWalletId": "wallet_2",
  "amount": 100.00,
  "status": "PROCESSED",
  "createdAt": "2024-01-15T10:30:00",
  "message": "Transfer completed successfully"
}
```

### Test Idempotency (Send Same Request Again)

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-123",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 100.00
  }'
```

**Expected:** Same response, same transferId, HTTP 200 OK

### Get Transfer by ID

```bash
curl http://localhost:8080/transfers/{transferId}
```

## 4. Run Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TransferServiceImplTest

# Run integration tests
mvn test -Dtest=TransferIntegrationTest
```

## 5. Check Database

```bash
# Connect to PostgreSQL
docker exec -it postgres-wallet psql -U postgres -d wallet_db

# Check wallets
SELECT * FROM wallets;

# Check transfers
SELECT * FROM transfers;

# Check ledger entries
SELECT * FROM ledger_entries;

# Verify ledger balances (should be 0)
SELECT SUM(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) as balance
FROM ledger_entries;
```

## Sample Wallets

The application includes pre-populated wallets:
- `wallet_1`: 1000.00
- `wallet_2`: 500.00
- `wallet_3`: 2000.00

## Common Issues

### Port 8080 in use
Change port in `src/main/resources/application.properties`:
```properties
server.port=8081
```

### Database connection failed
Ensure PostgreSQL is running:
```bash
docker ps
```

### Tests failing
Ensure you have Java 17:
```bash
java -version
```

## Next Steps

- Read [DESIGN.md](DESIGN.md) for architecture details
- Read [README.md](README.md) for complete documentation
- Review test cases in `src/test/java/com/wallet/`

