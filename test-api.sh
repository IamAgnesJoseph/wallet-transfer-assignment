#!/bin/bash

# Wallet Transfer Service API Test Script
# This script tests the main functionality of the wallet transfer service

BASE_URL="http://localhost:8080"

echo "========================================="
echo "Wallet Transfer Service - API Test"
echo "========================================="
echo ""

# Test 1: Create a successful transfer
echo "Test 1: Create a successful transfer"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-1",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 100.00
  }' | jq '.'
echo ""
echo ""

# Test 2: Test idempotency - send same request again
echo "Test 2: Test idempotency (duplicate request)"
echo "-------------------------------------"
echo "Sending the same request again..."
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-1",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 100.00
  }' | jq '.'
echo ""
echo "Note: Should return the same transferId and HTTP 200"
echo ""
echo ""

# Test 3: Create another transfer
echo "Test 3: Create another transfer"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-2",
    "fromWalletId": "wallet_2",
    "toWalletId": "wallet_3",
    "amount": 50.00
  }' | jq '.'
echo ""
echo ""

# Test 4: Test insufficient balance
echo "Test 4: Test insufficient balance"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-3",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 10000.00
  }' | jq '.'
echo ""
echo "Note: Should return 400 Bad Request with insufficient balance error"
echo ""
echo ""

# Test 5: Test same wallet transfer (invalid)
echo "Test 5: Test same wallet transfer (invalid)"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-4",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_1",
    "amount": 50.00
  }' | jq '.'
echo ""
echo "Note: Should return 400 Bad Request - cannot transfer to same wallet"
echo ""
echo ""

# Test 6: Test validation - missing idempotency key
echo "Test 6: Test validation (missing idempotency key)"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": 50.00
  }' | jq '.'
echo ""
echo "Note: Should return 400 Bad Request with validation error"
echo ""
echo ""

# Test 7: Test validation - negative amount
echo "Test 7: Test validation (negative amount)"
echo "-------------------------------------"
curl -X POST $BASE_URL/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "test-transfer-5",
    "fromWalletId": "wallet_1",
    "toWalletId": "wallet_2",
    "amount": -50.00
  }' | jq '.'
echo ""
echo "Note: Should return 400 Bad Request with validation error"
echo ""
echo ""

echo "========================================="
echo "Test completed!"
echo "========================================="
echo ""
echo "To verify the results in the database:"
echo "docker exec -it postgres-wallet psql -U postgres -d wallet_db"
echo ""
echo "Then run:"
echo "  SELECT * FROM wallets;"
echo "  SELECT * FROM transfers;"
echo "  SELECT * FROM ledger_entries;"
echo "  SELECT * FROM idempotency_records;"
echo ""
echo "To verify ledger balance (should be 0):"
echo "  SELECT SUM(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) FROM ledger_entries;"

