-- Wallets table: one per user, balance in paise (integer), never negative
CREATE TABLE IF NOT EXISTS wallets (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     TEXT            NOT NULL,
    balance     BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Gate 1: UNIQUE constraint prevents duplicate wallets per user
    CONSTRAINT wallets_user_id_unique UNIQUE (user_id),
    -- Gate 3: DB-enforced non-negative balance
    CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0)
);

-- Transfers table: one per idempotency_key, all amounts in paise
CREATE TABLE IF NOT EXISTS transfers (
    id                BIGSERIAL       PRIMARY KEY,
    idempotency_key   TEXT            NOT NULL,
    body_hash         TEXT            NOT NULL,
    from_wallet_id    BIGINT          NOT NULL REFERENCES wallets(id),
    to_wallet_id      BIGINT          NOT NULL REFERENCES wallets(id),
    amount_paise      BIGINT          NOT NULL,
    status            TEXT            NOT NULL DEFAULT 'PENDING',
    decline_reason    TEXT,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Gate 2: UNIQUE constraint prevents duplicate transfers per key
    CONSTRAINT transfers_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT transfers_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT transfers_different_wallets CHECK (from_wallet_id <> to_wallet_id)
);

CREATE INDEX IF NOT EXISTS idx_transfers_from ON transfers(from_wallet_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to   ON transfers(to_wallet_id);
