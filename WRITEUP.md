# Wallet & P2P Transfer Service — Technical Write-Up

## Data Model

Two tables in PostgreSQL, all monetary values stored as **`BIGINT` paise** (never floats, never rupees-as-decimal):

| Table | Key Columns | Constraints |
|---|---|---|
| `wallets` | `id` (PK), `user_id` (UNIQUE), `balance`, `created_at` | `CHECK (balance >= 0)` |
| `transfers` | `id` (PK), `idempotency_key` (UNIQUE), `body_hash`, `from_wallet_id` (FK), `to_wallet_id` (FK), `amount_paise`, `status`, `decline_reason`, `created_at` | `CHECK (amount_paise > 0)` |

The invariants are enforced at the **database level** — unique constraints, check constraints, and foreign keys — not just in application code.

---

## Simplest-Correct Mechanism: Conditional UPDATE

### What I used

Atomic conditional debit via:
```sql
UPDATE wallets SET balance = balance - ? WHERE id = ? AND balance >= ?
```

If `rows_affected = 0`, the transfer is declined (insufficient funds). If `rows_affected = 1`, the debit succeeded and we proceed with the credit. Both operations happen in a **single transaction** at the default `READ COMMITTED` isolation level.

### Why this is the simplest correct approach

1. **No explicit row locks.** Each `UPDATE` takes an implicit row-level lock on the wallet row it modifies. We never hold locks on two wallet rows simultaneously — we debit one, release it (the UPDATE returns), then credit the other.

2. **Deadlock-free by construction.** Because we don't use `SELECT … FOR UPDATE` to pre-lock two rows, there is no scenario where transfer A→B holds wallet A's lock waiting for B, while transfer B→A holds wallet B's lock waiting for A. The conditional `UPDATE` approach sidesteps this entirely.

3. **No serializable overhead.** `SERIALIZABLE` isolation is correct but forces retry loops on serialization failures. It's unnecessary here because the conditional `UPDATE` already provides the atomicity we need.

### Heavier alternatives I rejected

| Alternative | Why rejected |
|---|---|
| `SELECT … FOR UPDATE` with sorted lock order | Correct but heavier: requires locking two rows, deterministic ordering to avoid deadlocks, and more complex code. Same guarantees, more machinery. |
| `SERIALIZABLE` isolation | Correct but adds retry-on-serialization-failure cost. Every concurrent transaction on overlapping rows must be retried. Overkill for this access pattern. |
| Read balance → subtract in app → write back | **Broken.** Classic lost-update under concurrency — money is created or destroyed. |

---

## Where Idempotency Lives

The `idempotency_key` uniqueness is enforced via a **UNIQUE constraint** on the `transfers` table, committed in the **same transaction** as the debit/credit.

### Mechanism

```sql
INSERT INTO transfers (idempotency_key, body_hash, ...)
VALUES (?, ?, ...)
ON CONFLICT (idempotency_key) DO NOTHING
RETURNING id
```

- **If the insert succeeds** (new key) → proceed with debit/credit in the same transaction.
- **If the insert returns nothing** (key exists) → check `body_hash`. Same hash → return the existing transfer (idempotent replay). Different hash → `409 Conflict`.

### Why same-transaction matters

If the idempotency check were in a **separate transaction** (check-then-insert), two concurrent requests with the same key could both pass the check, both insert, and both debit — a double-apply. By making the `INSERT` and the debit/credit share the same `COMMIT`, PostgreSQL's unique constraint blocking behavior serializes concurrent duplicates: the second request blocks at the `INSERT` until the first commits or rolls back.

---

## Consistency vs. Availability

**Chose: Consistency (CP).** This is a money workload — a double-spend or lost update is worse than temporary unavailability.

**What I give up:** Under extreme contention, transactions may block briefly waiting for row locks held by concurrent transfers. This is an acceptable latency cost for guaranteed correctness. I do not sacrifice availability for partition tolerance because this is a single-region, single-database deployment — network partitions between app and DB would affect both CP and AP systems equally.

---

## AI: Directed vs. Decided

| What | Role |
|---|---|
| Choice of conditional `UPDATE` over `FOR UPDATE` | **Directed** — I chose the approach based on deadlock avoidance analysis; AI helped with syntax |
| `ON CONFLICT DO NOTHING RETURNING id` pattern for idempotency | **Directed** — I identified the TOCTOU risk and chose this pattern; AI helped implement it |
| Dockerfile multi-stage + non-root | **Directed** — I specified the requirements; AI generated the Dockerfile |
| Burst test script | **Directed** — I designed the test scenarios; AI helped with bash concurrency patterns |
| Spring Boot wiring / boilerplate | **AI-assisted** — standard framework patterns, AI typed, I reviewed |

---

## Free-Tier Cost Note

| Service | Provider | Cost |
|---|---|---|
| App hosting | Render.com Web Service (free) | ₹0 |
| PostgreSQL | Render.com Postgres (free, 256 MB) | ₹0 |
| **Total** | | **₹0** |

No credit card required.
