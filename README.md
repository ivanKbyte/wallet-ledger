# Wallet Ledger API

A RESTful wallet ledger service that supports creating wallets, crediting/debiting funds with idempotency guarantees, and paginated transaction history. Built with Spring Boot 3.5, PostgreSQL 16, and Flyway.

## Prerequisites

- Java 21
- Docker (for the local database and for running the test suite)

## Local Setup

**1. Start the database**

```bash
docker compose up -d
```

**2. Run the application**

```bash
./gradlew bootRun
```

The API is available at `http://localhost:8080`.

## Testing

Ensure Docker is running — Testcontainers spins up a real PostgreSQL 16 container for the test suite.

```bash
./gradlew test
```

## API Reference

All endpoints require an `X-User-Id` header containing the user's UUID.

---

### Create Wallet

```bash
curl -s -X POST http://localhost:8080/wallets \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
```

Returns `201 Created`. Returns `409 Conflict` if a wallet already exists for that user.

---

### Get Wallet

```bash
curl -s http://localhost:8080/wallets/me \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
```

---

### Credit

```bash
curl -s -X POST http://localhost:8080/wallets/me/credit \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 10000,
    "reference": "top-up",
    "idempotencyKey": "credit-001"
  }'
```

Repeating the same `idempotencyKey` returns the original transaction unchanged (idempotent).

---

### Debit

```bash
curl -s -X POST http://localhost:8080/wallets/me/debit \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 2500,
    "reference": "purchase",
    "idempotencyKey": "debit-001"
  }'
```

Returns `422 Unprocessable Entity` if the wallet has insufficient balance.

---

### Get Transactions

```bash
curl -s "http://localhost:8080/wallets/me/transactions?page=0&size=20" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001"
```

Returns a paginated list of transactions ordered by most recent first. `size` must be between 1 and 100 (default 20).
