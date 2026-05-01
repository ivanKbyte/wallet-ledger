CREATE TABLE wallets (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL UNIQUE,
    balance     BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID        NOT NULL REFERENCES wallets(id),
    type             VARCHAR(10) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount           BIGINT      NOT NULL CHECK (amount > 0),
    reference        VARCHAR     NOT NULL,
    idempotency_key  VARCHAR     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wallet_idempotency UNIQUE (wallet_id, idempotency_key)
);
