# Wallet Transfer Service API Test Script (PowerShell)
# This script tests the main functionality of the wallet transfer service

$BaseUrl = "http://localhost:8080"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Wallet Transfer Service - API Test" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Create a successful transfer
Write-Host "Test 1: Create a successful transfer" -ForegroundColor Yellow
Write-Host "-------------------------------------"
$body1 = @{
    idempotencyKey = "test-transfer-1"
    fromWalletId = "wallet_1"
    toWalletId = "wallet_2"
    amount = 100.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "$BaseUrl/transfers" -Method Post -Body $body1 -ContentType "application/json" | ConvertTo-Json
Write-Host ""

# Test 2: Test idempotency
Write-Host "Test 2: Test idempotency (duplicate request)" -ForegroundColor Yellow
Write-Host "-------------------------------------"
Write-Host "Sending the same request again..."
Invoke-RestMethod -Uri "$BaseUrl/transfers" -Method Post -Body $body1 -ContentType "application/json" | ConvertTo-Json
Write-Host "Note: Should return the same transferId and HTTP 200" -ForegroundColor Green
Write-Host ""

# Test 3: Create another transfer
Write-Host "Test 3: Create another transfer" -ForegroundColor Yellow
Write-Host "-------------------------------------"
$body2 = @{
    idempotencyKey = "test-transfer-2"
    fromWalletId = "wallet_2"
    toWalletId = "wallet_3"
    amount = 50.00
} | ConvertTo-Json

Invoke-RestMethod -Uri "$BaseUrl/transfers" -Method Post -Body $body2 -ContentType "application/json" | ConvertTo-Json
Write-Host ""

# Test 4: Test insufficient balance
Write-Host "Test 4: Test insufficient balance" -ForegroundColor Yellow
Write-Host "-------------------------------------"
$body3 = @{
    idempotencyKey = "test-transfer-3"
    fromWalletId = "wallet_1"
    toWalletId = "wallet_2"
    amount = 10000.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$BaseUrl/transfers" -Method Post -Body $body3 -ContentType "application/json" | ConvertTo-Json
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
}
Write-Host "Note: Should return 400 Bad Request with insufficient balance error" -ForegroundColor Green
Write-Host ""

# Test 5: Test same wallet transfer
Write-Host "Test 5: Test same wallet transfer (invalid)" -ForegroundColor Yellow
Write-Host "-------------------------------------"
$body4 = @{
    idempotencyKey = "test-transfer-4"
    fromWalletId = "wallet_1"
    toWalletId = "wallet_1"
    amount = 50.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$BaseUrl/transfers" -Method Post -Body $body4 -ContentType "application/json" | ConvertTo-Json
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
}
Write-Host "Note: Should return 400 Bad Request - cannot transfer to same wallet" -ForegroundColor Green
Write-Host ""

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Test completed!" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To verify the results in the database:" -ForegroundColor Yellow
Write-Host "docker exec -it postgres-wallet psql -U postgres -d wallet_db"
Write-Host ""
Write-Host "Then run:" -ForegroundColor Yellow
Write-Host "  SELECT * FROM wallets;"
Write-Host "  SELECT * FROM transfers;"
Write-Host "  SELECT * FROM ledger_entries;"
Write-Host "  SELECT * FROM idempotency_records;"
Write-Host ""
Write-Host "To verify ledger balance (should be 0):" -ForegroundColor Yellow
Write-Host "  SELECT SUM(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) FROM ledger_entries;"

