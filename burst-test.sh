#!/usr/bin/env bash
# ==============================================================================
# Wallet Service — Burst Test Script
# Probes all three rubric gates against a deployed or local URL.
# Usage: ./burst-test.sh [base_url]
# ==============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
bold()  { printf "\033[1m%s\033[0m\n" "$1"; }

bold "╔═══════════════════════════════════════════════════════════╗"
bold "║         Wallet Service — Burst Test Suite                 ║"
echo "║  Target: $BASE_URL"
bold "╚═══════════════════════════════════════════════════════════╝"
echo ""

# ─────────────────────────────────────────────────────────────────
# GATE 1: Race-free get-or-create
# ─────────────────────────────────────────────────────────────────
bold "═══ GATE 1: Concurrent get-or-create (50 requests) ═══"
G1_USER="burst-g1-$(date +%s)-$RANDOM"
echo "User: $G1_USER"

for i in $(seq 1 50); do
  curl -s -X POST "$BASE_URL/wallets" \
    -H "Authorization: Bearer $G1_USER" \
    -H "Content-Type: application/json" > "$TMPDIR/g1_$i.json" &
done
wait

G1_IDS=$(cat "$TMPDIR"/g1_*.json | jq -r '.id' 2>/dev/null | sort -u | wc -l | tr -d ' ')
if [ "$G1_IDS" -eq 1 ]; then
  green "✅ PASS: Exactly 1 wallet created"
  ((PASS++))
else
  red "❌ FAIL: Expected 1 unique wallet ID, got $G1_IDS"
  ((FAIL++))
fi
echo ""

# ─────────────────────────────────────────────────────────────────
# GATE 2: Idempotent exactly-once transfer
# ─────────────────────────────────────────────────────────────────
bold "═══ GATE 2: Idempotent retry storm (30 concurrent, same key) ═══"

# Create two wallets with balance for testing
G2_SENDER="burst-g2-sender-$(date +%s)"
G2_RECVR="burst-g2-recvr-$(date +%s)"

WALLET_A=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $G2_SENDER" \
  -H "Content-Type: application/json" \
  -d '{"initial_balance_paise": 1000000}' | jq -r '.id')

WALLET_B=$(curl -s -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer $G2_RECVR" \
  -H "Content-Type: application/json" | jq -r '.id')

echo "Wallet A (sender): $WALLET_A (balance: 1000000 paise)"
echo "Wallet B (receiver): $WALLET_B (balance: 0 paise)"

IDEM_KEY="idem-$(date +%s)-$RANDOM"
echo "Idempotency key: $IDEM_KEY"

# Fire 30 concurrent transfers with the SAME idempotency key
for i in $(seq 1 30); do
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Content-Type: application/json" \
    -d "{\"from\":$WALLET_A,\"to\":$WALLET_B,\"amount_paise\":500,\"idempotency_key\":\"$IDEM_KEY\"}" \
    > "$TMPDIR/g2_$i.json" &
done
wait

# Check: exactly one debit (balance should be 999500, not less)
BAL_A=$(curl -s "$BASE_URL/wallets/$WALLET_A" | jq -r '.balance')
EXPECTED_BAL=$((1000000 - 500))
if [ "$BAL_A" -eq "$EXPECTED_BAL" ]; then
  green "✅ PASS: Exactly one debit applied (balance: $BAL_A)"
  ((PASS++))
else
  red "❌ FAIL: Expected balance $EXPECTED_BAL, got $BAL_A (double debit?)"
  ((FAIL++))
fi

# Check: all responses return the same transfer ID
G2_TRANSFER_IDS=$(cat "$TMPDIR"/g2_*.json | jq -r '.id' 2>/dev/null | sort -u | wc -l | tr -d ' ')
if [ "$G2_TRANSFER_IDS" -eq 1 ]; then
  green "✅ PASS: All 30 responses returned the same transfer ID"
  ((PASS++))
else
  red "❌ FAIL: Expected 1 unique transfer ID, got $G2_TRANSFER_IDS"
  ((FAIL++))
fi

# Check: same key + different body → 409
CONFLICT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/transfers" \
  -H "Content-Type: application/json" \
  -d "{\"from\":$WALLET_A,\"to\":$WALLET_B,\"amount_paise\":999,\"idempotency_key\":\"$IDEM_KEY\"}")
if [ "$CONFLICT_STATUS" -eq 409 ]; then
  green "✅ PASS: Same key + different body → 409 Conflict"
  ((PASS++))
else
  red "❌ FAIL: Same key + different body → expected 409, got $CONFLICT_STATUS"
  ((FAIL++))
fi
echo ""

# ─────────────────────────────────────────────────────────────────
# GATE 3: Conservation + no-overdraft under contention
# ─────────────────────────────────────────────────────────────────
bold "═══ GATE 3: Conservation under contention (100 concurrent transfers) ═══"

# Seed 4 wallets with known balances
declare -a G3_WALLETS
INITIAL_TOTAL=0
for i in 1 2 3 4; do
  G3_USER="burst-g3-user$i-$(date +%s)-$RANDOM"
  BAL=$((10000 * i))
  WID=$(curl -s -X POST "$BASE_URL/wallets" \
    -H "Authorization: Bearer $G3_USER" \
    -H "Content-Type: application/json" \
    -d "{\"initial_balance_paise\": $BAL}" | jq -r '.id')
  G3_WALLETS+=("$WID")
  INITIAL_TOTAL=$((INITIAL_TOTAL + BAL))
  echo "  Wallet $WID: $BAL paise"
done
echo "Initial total: $INITIAL_TOTAL paise"
echo "Firing 100 concurrent transfers (including A↔B crosses and overdraw attempts)..."

# Fire 100 concurrent transfers among the 4 wallets
for i in $(seq 1 100); do
  FROM_IDX=$((RANDOM % 4))
  TO_IDX=$(( (FROM_IDX + 1 + RANDOM % 3) % 4 ))
  FROM_W=${G3_WALLETS[$FROM_IDX]}
  TO_W=${G3_WALLETS[$TO_IDX]}
  AMT=$((RANDOM % 8000 + 100))
  KEY="g3-$i-$(date +%s)-$RANDOM"
  curl -s -X POST "$BASE_URL/transfers" \
    -H "Content-Type: application/json" \
    -d "{\"from\":$FROM_W,\"to\":$TO_W,\"amount_paise\":$AMT,\"idempotency_key\":\"$KEY\"}" \
    > /dev/null &
done
wait
sleep 1

# Sum final balances
FINAL_TOTAL=0
ALL_NON_NEGATIVE=true
for WID in "${G3_WALLETS[@]}"; do
  BAL=$(curl -s "$BASE_URL/wallets/$WID" | jq -r '.balance')
  FINAL_TOTAL=$((FINAL_TOTAL + BAL))
  if [ "$BAL" -lt 0 ]; then
    ALL_NON_NEGATIVE=false
    red "  ❌ Wallet $WID has negative balance: $BAL"
  else
    echo "  Wallet $WID: $BAL paise"
  fi
done

if [ "$FINAL_TOTAL" -eq "$INITIAL_TOTAL" ]; then
  green "✅ PASS: Total balance conserved ($INITIAL_TOTAL → $FINAL_TOTAL)"
  ((PASS++))
else
  red "❌ FAIL: Total balance changed ($INITIAL_TOTAL → $FINAL_TOTAL, diff=$((FINAL_TOTAL - INITIAL_TOTAL)))"
  ((FAIL++))
fi

if $ALL_NON_NEGATIVE; then
  green "✅ PASS: No negative balances"
  ((PASS++))
else
  red "❌ FAIL: Negative balance detected"
  ((FAIL++))
fi
echo ""

# ─────────────────────────────────────────────────────────────────
# SUMMARY
# ─────────────────────────────────────────────────────────────────
bold "═══════════════════════════════════════════════════════════"
echo "Results: $PASS passed, $FAIL failed out of $((PASS + FAIL)) checks"
if [ "$FAIL" -eq 0 ]; then
  green "🎉 All gates passed!"
  exit 0
else
  red "⚠️  Some gates failed — review output above."
  exit 1
fi
