# Wallet Transfer Service

A robust wallet-to-wallet transfer service implementing idempotency, concurrency control, and double-entry ledger accounting.

## Features

- ✅ **Idempotent Transfers** - Exactly-once semantics using idempotency keys
- ✅ **Concurrency Safe** - Pessimistic locking prevents race conditions and double-spending
- ✅ **Double-Entry Ledger** - Every transfer creates balanced DEBIT and CREDIT entries
- ✅ **State Machine** - Safe state transitions (PENDING → PROCESSED/FAILED)
- ✅ **Transaction Safety** - ACID guarantees with SERIALIZABLE isolation
- ✅ **Clean Architecture** - Layered design (Controller → Service → Repository → Domain)
- ✅ **Comprehensive Tests** - Unit tests, integration tests, and concurrency tests

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **PostgreSQL** (production)
- **H2** (testing)
- **Flyway** (database migrations)
- **JUnit 5** (testing)
- **Maven** (build tool)

## Architecture

See [DESIGN.md](DESIGN.md) for detailed architecture and design decisions.

```
Controller Layer → Service Layer → Repository Layer → Database
     ↓                  ↓                 ↓
Request/Response   Business Logic   Data Access
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+ (for production)
- Docker (optional, for running PostgreSQL)

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd wallet-transfer-assignment
```

### 2. Start PostgreSQL (using Docker)

```bash
docker run --name postgres-wallet \
  -e POSTGRES_DB=wallet_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:15
```

Or use your local PostgreSQL installation and create the database:

```sql
CREATE DATABASE wallet_db;
```

### 3. Configure Database

Update `src/main/resources/application.properties` if needed:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wallet_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

### 4. Build the project

```bash
mvn clean install
```

### 5. Run the application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Documentation

### Create Transfer

**Endpoint:** `POST /transfers`

**Request:**
```json
{
  "idempotencyKey": "unique-key-123",
  "fromWalletId": "wallet_1",
  "toWalletId": "wallet_2",
  "amount": 100.00
}
```

**Response (201 Created):**
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

**Response for Duplicate Request (200 OK):**
Same response as original, demonstrating idempotency.

**Error Response (400 Bad Request):**
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance in wallet: wallet_1",
  "path": "/transfers"
}
```

### Get Transfer

**Endpoint:** `GET /transfers/{transferId}`

**Response (200 OK):**
```json
{
  "transferId": "550e8400-e29b-41d4-a716-446655440000",
  "fromWalletId": "wallet_1",
  "toWalletId": "wallet_2",
  "amount": 100.00,
  "status": "PROCESSED",
  "createdAt": "2024-01-15T10:30:00"
}
```

## Testing

### Run all tests

```bash
mvn test
```

### Run specific test class

```bash
mvn test -Dtest=TransferServiceImplTest
```

### Run integration tests

```bash
mvn test -Dtest=TransferIntegrationTest
```

### Test Coverage

The test suite includes:
- **Unit Tests**: Service layer logic, validation, state transitions
- **Integration Tests**: Full stack testing with database
- **Idempotency Tests**: Duplicate request handling
- **Concurrency Tests**: Concurrent transfers from same wallet
- **Ledger Tests**: Double-entry bookkeeping verification

## Database Schema

The application uses Flyway for database migrations. Schema is automatically created on startup.

### Tables

1. **wallets** - Wallet balances with optimistic locking
2. **transfers** - Transfer records with state machine
3. **ledger_entries** - Double-entry ledger for audit trail
4. **idempotency_records** - Idempotency key tracking

See [DESIGN.md](DESIGN.md) for detailed schema documentation.

## Sample Data

The application includes sample wallets for testing:
- `wallet_1`: Balance 1000.00
- `wallet_2`: Balance 500.00
- `wallet_3`: Balance 2000.00

## Design Decisions

### Idempotency Strategy
- Database-backed with unique constraint on idempotency_key
- Stores request/response payloads for exact replay
- Handles race conditions with DataIntegrityViolationException

### Concurrency Strategy
- SERIALIZABLE transaction isolation
- Pessimistic write locks (SELECT FOR UPDATE)
- Optimistic locking with @Version as backup
- Alphabetical lock ordering to prevent deadlocks

### Double-Entry Ledger
- Every transfer creates exactly 2 entries (DEBIT + CREDIT)
- Immutable audit trail
- Ledger always balances (sum = 0)

See [DESIGN.md](DESIGN.md) for complete design documentation.

## Project Structure

```
src/
├── main/
│   ├── java/com/wallet/
│   │   ├── controllers/        # REST controllers
│   │   ├── services/           # Business logic
│   │   │   └── impl/
│   │   ├── repositories/       # Data access
│   │   ├── entities/           # JPA entities
│   │   ├── models/             # DTOs
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── exceptions/         # Custom exceptions
│   │   └── WalletTransferApplication.java
│   └── resources/
│       ├── application.properties
│       └── db/migration/       # Flyway migrations
└── test/
    ├── java/com/wallet/
    │   ├── services/impl/      # Unit tests
    │   └── integration/        # Integration tests
    └── resources/
        └── application-test.properties
```

## Troubleshooting

### Database Connection Issues

If you see connection errors:
1. Ensure PostgreSQL is running: `docker ps`
2. Check database exists: `psql -U postgres -l`
3. Verify credentials in `application.properties`

### Port Already in Use

If port 8080 is in use, change it in `application.properties`:
```properties
server.port=8081
```

### Tests Failing

Ensure H2 database is in classpath (should be automatic with Maven).

## License

MIT License

## Assignment Context

This implementation was created for the Wallet Transfer Service coding assignment. See [ASSIGNMENT.md](ASSIGNMENT.md) for the original requirements.
