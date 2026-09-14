-- Phase 01 — one listing table for seven kinds.

CREATE TABLE listing (
    id              uuid PRIMARY KEY,
    owner_id        uuid        NOT NULL,
    kind            text        NOT NULL,   -- RENT_ITEM | SELL_ITEM | TRADE_SERVICE | SKILL_FOR_HIRE
                                            -- | TEACHING | SPACE_OR_VEHICLE | OPEN_NEED
    title           text        NOT NULL,
    description     text,
    price_minor     bigint,                 -- rate per unit, integer minor units, never a float
    unit            text,                   -- HOUR | DAY | WEEK | SESSION | ITEM
    deposit_minor   bigint      NOT NULL DEFAULT 0,
    buy_price_minor bigint,
    currency        text        NOT NULL DEFAULT 'EUR',
    lat             double precision NOT NULL,
    lon             double precision NOT NULL,
    point           geography(Point, 4326) GENERATED ALWAYS AS (
                        ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography
                    ) STORED,
    status          text        NOT NULL DEFAULT 'LIVE',   -- LIVE | PAUSED | UNLISTED
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT listing_price_present CHECK (
        kind = 'OPEN_NEED' OR price_minor IS NOT NULL OR buy_price_minor IS NOT NULL)
);

-- ST_DWithin uses this; distance is never computed in Java and filtered after.
CREATE INDEX listing_point_gix ON listing USING gist (point);
CREATE INDEX listing_owner_idx ON listing (owner_id, created_at DESC);
CREATE INDEX listing_live_idx  ON listing (kind, created_at DESC) WHERE status = 'LIVE';

CREATE TABLE listing_photo (
    id         uuid PRIMARY KEY,
    listing_id uuid NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    object_key text NOT NULL,
    url        text NOT NULL,
    sort_order int  NOT NULL DEFAULT 0
);

CREATE INDEX listing_photo_listing_idx ON listing_photo (listing_id, sort_order);

CREATE TABLE listing_tag (
    listing_id uuid NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    slug       text NOT NULL,
    PRIMARY KEY (listing_id, slug)
);

CREATE TABLE availability_rule (
    id         uuid PRIMARY KEY,
    listing_id uuid NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    weekday    int  NOT NULL CHECK (weekday BETWEEN 1 AND 7),   -- ISO: Monday = 1
    from_time  time NOT NULL,
    to_time    time NOT NULL,
    CONSTRAINT availability_rule_order CHECK (from_time < to_time)
);

CREATE INDEX availability_rule_listing_idx ON availability_rule (listing_id);

CREATE TABLE availability_block (
    id         uuid PRIMARY KEY,
    listing_id uuid NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    date_from  date NOT NULL,
    date_to    date NOT NULL,
    reason     text NOT NULL DEFAULT 'manual',   -- booked | manual
    request_id uuid,
    CONSTRAINT availability_block_order CHECK (date_from <= date_to)
);

CREATE INDEX availability_block_listing_idx ON availability_block (listing_id, date_from, date_to);
-- One block per accepted request, so a replayed RequestAccepted event is a no-op.
CREATE UNIQUE INDEX availability_block_request_uidx ON availability_block (request_id)
    WHERE request_id IS NOT NULL;

-- A read-only copy of where members are, fed by radius.user.v1. It saves a
-- synchronous call to user-service on every card render.
CREATE TABLE member_location (
    user_id      uuid PRIMARY KEY,
    display_name text,
    photo_url    text,
    area_label   text,
    lat          double precision,
    lon          double precision,
    radius_km    int NOT NULL DEFAULT 5,
    updated_at   timestamptz NOT NULL DEFAULT now()
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
