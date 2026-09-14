-- Phase 00 — accounts, one profile per person, tags, refresh tokens.
-- "user" is reserved in Postgres, hence app_user.

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    phone         text UNIQUE,
    email         text UNIQUE,
    password_hash text,
    display_name  text        NOT NULL,
    photo_url     text,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_user_contactable CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

CREATE TABLE profile (
    user_id          uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    area_label       text,
    -- Coarse home point. Java writes lat/lon; Postgres derives the geography so
    -- the spatial index stays correct without an ORM spatial mapping.
    lat              double precision,
    lon              double precision,
    home_point       geography(Point, 4326) GENERATED ALWAYS AS (
                         CASE WHEN lat IS NULL OR lon IS NULL THEN NULL
                              ELSE ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography END
                     ) STORED,
    search_radius_km int         NOT NULL DEFAULT 5,
    open_to_requests boolean     NOT NULL DEFAULT true,
    bio              text,
    rating_avg       numeric(3,2),
    completed_count  int         NOT NULL DEFAULT 0,
    verified         boolean     NOT NULL DEFAULT false,
    updated_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX profile_home_point_gix ON profile USING gist (home_point);

CREATE TABLE tag (
    id    uuid PRIMARY KEY,
    slug  text UNIQUE NOT NULL,
    label text        NOT NULL,
    kind  text        NOT NULL   -- skill | teach | need | hobby | item
);

CREATE INDEX tag_label_trgm ON tag USING gin (label gin_trgm_ops);

CREATE TABLE profile_tag (
    profile_id uuid NOT NULL REFERENCES profile (user_id) ON DELETE CASCADE,
    tag_id     uuid NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    relation   text NOT NULL,     -- has | teaches | needs | enjoys
    PRIMARY KEY (profile_id, tag_id, relation)
);

-- Rotating refresh tokens: one row per issued token, revoked on use.
CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,          -- the jti
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    user_agent  text
);

CREATE INDEX refresh_token_user_idx ON refresh_token (user_id) WHERE revoked_at IS NULL;

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
