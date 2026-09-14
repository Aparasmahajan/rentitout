-- Phase 02 — the search read model.
--
-- This table is a projection of radius.listing.v1 and radius.user.v1. It is
-- disposable: delete it, replay both topics from the beginning, and it comes
-- back. Nothing writes here except the consumer.

CREATE TABLE listing_doc (
    id              uuid PRIMARY KEY,
    owner_id        uuid        NOT NULL,
    owner_name      text,
    owner_photo     text,
    owner_rating    numeric(3,2),
    kind            text        NOT NULL,
    title           text        NOT NULL,
    description     text,
    tags            text[]      NOT NULL DEFAULT '{}',
    price_minor     bigint,
    unit            text,
    deposit_minor   bigint,
    buy_price_minor bigint,
    currency        text        NOT NULL DEFAULT 'EUR',
    photo_url       text,
    area_label      text,
    lat             double precision NOT NULL,
    lon             double precision NOT NULL,
    point           geography(Point, 4326) GENERATED ALWAYS AS (
                        ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography
                    ) STORED,
    status          text        NOT NULL DEFAULT 'LIVE',
    published_at    timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    search_vector   tsvector
);

CREATE INDEX listing_doc_point_gix  ON listing_doc USING gist (point);
CREATE INDEX listing_doc_vector_gin ON listing_doc USING gin (search_vector);
CREATE INDEX listing_doc_tags_gin   ON listing_doc USING gin (tags);
CREATE INDEX listing_doc_live_idx   ON listing_doc (kind) WHERE status = 'LIVE';

-- The vector is maintained by trigger, not by the application: it can never
-- drift out of step with the row it describes.
CREATE OR REPLACE FUNCTION listing_doc_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(array_to_string(NEW.tags, ' '), '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'C') ||
        setweight(to_tsvector('simple', coalesce(NEW.owner_name, '')), 'D');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER listing_doc_vector_trg
    BEFORE INSERT OR UPDATE ON listing_doc
    FOR EACH ROW EXECUTE FUNCTION listing_doc_vector();

-- A member is searchable in their own right — "someone who knows video editing".
CREATE TABLE member_doc (
    user_id       uuid PRIMARY KEY,
    display_name  text,
    photo_url     text,
    area_label    text,
    bio           text,
    tags          text[] NOT NULL DEFAULT '{}',
    rating_avg    numeric(3,2),
    radius_km     int NOT NULL DEFAULT 5,
    open_to_requests boolean NOT NULL DEFAULT true,
    lat           double precision,
    lon           double precision,
    point         geography(Point, 4326) GENERATED ALWAYS AS (
                      CASE WHEN lat IS NULL OR lon IS NULL THEN NULL
                           ELSE ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography END
                  ) STORED,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    search_vector tsvector
);

CREATE INDEX member_doc_point_gix  ON member_doc USING gist (point);
CREATE INDEX member_doc_vector_gin ON member_doc USING gin (search_vector);

CREATE OR REPLACE FUNCTION member_doc_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(array_to_string(NEW.tags, ' '), '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.display_name, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.bio, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER member_doc_vector_trg
    BEFORE INSERT OR UPDATE ON member_doc
    FOR EACH ROW EXECUTE FUNCTION member_doc_vector();

CREATE TABLE saved_search (
    id               uuid PRIMARY KEY,
    user_id          uuid        NOT NULL,
    label            text,
    raw_query        text,
    parsed_json      text,
    radius_km        int         NOT NULL DEFAULT 5,
    digest_frequency text        NOT NULL DEFAULT 'daily',   -- daily | weekly | off
    last_run_at      timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX saved_search_user_idx ON saved_search (user_id);
CREATE INDEX saved_search_digest_idx ON saved_search (digest_frequency, last_run_at);

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
