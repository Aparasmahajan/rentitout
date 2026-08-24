-- Phase 05 — the ledger. The surfaces already exist from Phase 03; this is what
-- switches on underneath them.

-- Percentage in basis points so the fee is exact integer arithmetic: 250 = 2.5%.
-- It reads zero until the day someone changes a row here.
CREATE TABLE fee_config (
    id            uuid PRIMARY KEY,
    kind          text UNIQUE,               -- NULL = the default for every kind
    percent_bps   int         NOT NULL DEFAULT 0,
    cap_minor     bigint,                    -- NULL = uncapped
    min_fee_minor bigint      NOT NULL DEFAULT 0,
    active_from   timestamptz NOT NULL DEFAULT now()
);

INSERT INTO fee_config (id, kind, percent_bps, cap_minor, min_fee_minor)
VALUES (gen_random_uuid(), NULL, 0, NULL, 0);

CREATE TABLE payment (
    id             uuid PRIMARY KEY,
    request_id     uuid        NOT NULL,
    payer_id       uuid        NOT NULL,
    payee_id       uuid        NOT NULL,
    gateway_ref    text UNIQUE,               -- the gateway's id; the webhook keys on it
    amount_minor   bigint      NOT NULL,
    fee_minor      bigint      NOT NULL DEFAULT 0,
    deposit_minor  bigint      NOT NULL DEFAULT 0,
    currency       text        NOT NULL DEFAULT 'EUR',
    state          text        NOT NULL DEFAULT 'PENDING',  -- PENDING | AUTHORISED | SETTLED | FAILED | REFUNDED
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

-- One payment per request. The constraint, not the code, is what makes a
-- retried "create intent" call safe.
CREATE UNIQUE INDEX payment_request_uidx ON payment (request_id);
CREATE INDEX payment_payee_idx ON payment (payee_id, created_at DESC);

CREATE TABLE deposit_hold (
    id           uuid PRIMARY KEY,
    request_id   uuid UNIQUE NOT NULL,
    payer_id     uuid        NOT NULL,
    amount_minor bigint      NOT NULL,
    held_at      timestamptz NOT NULL DEFAULT now(),
    released_at  timestamptz,
    claim_state  text        NOT NULL DEFAULT 'NONE'   -- NONE | OPEN | UPHELD | REJECTED
);

CREATE TABLE payout (
    id            uuid PRIMARY KEY,
    owner_id      uuid        NOT NULL,
    amount_minor  bigint      NOT NULL,
    fee_minor     bigint      NOT NULL DEFAULT 0,
    currency      text        NOT NULL DEFAULT 'EUR',
    period        text        NOT NULL,
    state         text        NOT NULL DEFAULT 'PENDING',   -- PENDING | PAID | FAILED
    statement_ref text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    paid_at       timestamptz
);

CREATE INDEX payout_owner_idx ON payout (owner_id, created_at DESC);
CREATE UNIQUE INDEX payout_owner_period_uidx ON payout (owner_id, period);

-- Every webhook body we ever accepted, so a replay is detected in the database
-- rather than in Redis. Money gets the stronger guarantee.
CREATE TABLE gateway_event (
    id          uuid PRIMARY KEY,
    gateway_ref text UNIQUE NOT NULL,
    event_type  text        NOT NULL,
    payload     text        NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id           uuid PRIMARY KEY,
    topic        text        NOT NULL,
    event_key    text        NOT NULL,
    event_type   text        NOT NULL,
    payload      text        NOT NULL,
    created_at   timestamptz NOT NULL,
    published_at timestamptz,
    attempts     int         NOT NULL DEFAULT 0
);

CREATE INDEX outbox_unpublished_idx ON outbox_event (created_at) WHERE published_at IS NULL;
