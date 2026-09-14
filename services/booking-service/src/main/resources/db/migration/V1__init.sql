-- Phase 03 — requests, their audit trail, and one message thread per request.

CREATE TABLE booking_request (
    id             uuid PRIMARY KEY,
    listing_id     uuid        NOT NULL,
    listing_title  text        NOT NULL,
    requester_id   uuid        NOT NULL,
    owner_id       uuid        NOT NULL,
    start_date     date        NOT NULL,
    end_date       date        NOT NULL,
    units          int         NOT NULL CHECK (units > 0),
    unit           text        NOT NULL,
    rate_minor     bigint      NOT NULL,
    amount_minor   bigint      NOT NULL,
    deposit_minor  bigint      NOT NULL DEFAULT 0,
    fee_minor      bigint      NOT NULL DEFAULT 0,
    total_minor    bigint      NOT NULL,
    currency       text        NOT NULL DEFAULT 'EUR',
    message        text,
    status         text        NOT NULL DEFAULT 'SENT',
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    accepted_at    timestamptz,
    completed_at   timestamptz,
    expires_at     timestamptz NOT NULL,
    CONSTRAINT booking_request_not_self CHECK (requester_id <> owner_id),
    CONSTRAINT booking_request_dates CHECK (start_date <= end_date)
);

CREATE INDEX booking_request_requester_idx ON booking_request (requester_id, created_at DESC);
CREATE INDEX booking_request_owner_idx     ON booking_request (owner_id, created_at DESC);
CREATE INDEX booking_request_listing_idx   ON booking_request (listing_id, start_date);
CREATE INDEX booking_request_expiry_idx    ON booking_request (expires_at) WHERE status = 'SENT';

-- Every transition is written down. "Who declined this and when" is a support
-- question that gets asked, and a status column alone cannot answer it.
CREATE TABLE request_transition (
    id          uuid PRIMARY KEY,
    request_id  uuid        NOT NULL REFERENCES booking_request (id) ON DELETE CASCADE,
    from_status text,
    to_status   text        NOT NULL,
    actor_id    uuid,
    reason      text,
    at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX request_transition_request_idx ON request_transition (request_id, at);

CREATE TABLE message (
    id         uuid PRIMARY KEY,
    request_id uuid        NOT NULL REFERENCES booking_request (id) ON DELETE CASCADE,
    sender_id  uuid        NOT NULL,
    body       text        NOT NULL,
    sent_at    timestamptz NOT NULL DEFAULT now(),
    read_at    timestamptz
);

CREATE INDEX message_request_idx ON message (request_id, sent_at);
CREATE INDEX message_unread_idx  ON message (request_id, sender_id) WHERE read_at IS NULL;

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
