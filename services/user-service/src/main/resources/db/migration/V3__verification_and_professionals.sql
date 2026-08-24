-- Roles, ID checks, and the professional profile behind a home visit.
--
-- The distinction that matters here: a member's home_point is deliberately
-- fuzzed to ~100 m because it is where they sleep. A professional's SHOP point
-- is a business address they are publishing on purpose, so it is stored exactly
-- and is safe to show on a map.

ALTER TABLE app_user ADD COLUMN role text NOT NULL DEFAULT 'MEMBER';
ALTER TABLE app_user ADD CONSTRAINT app_user_role_known CHECK (role IN ('MEMBER', 'ADMIN'));

-- One request per person per kind, open at a time. The partial unique index is
-- what stops someone spamming the review queue by submitting repeatedly.
CREATE TABLE verification_request (
    id            uuid PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind          text        NOT NULL,   -- IDENTITY | PROFESSIONAL
    state         text        NOT NULL DEFAULT 'SUBMITTED',
                                          -- SUBMITTED | IN_REVIEW | APPROVED | REJECTED | WITHDRAWN
    -- Object keys only. The bytes live in the restricted bucket prefix and can
    -- be deleted on the retention date without losing this row's audit trail.
    evidence_keys text,
    member_note   text,
    decision_note text,
    decided_by    uuid REFERENCES app_user (id),
    submitted_at  timestamptz NOT NULL DEFAULT now(),
    decided_at    timestamptz,
    -- When the evidence should be destroyed. Null until a decision is made.
    purge_after   date,
    CONSTRAINT verification_kind_known CHECK (kind IN ('IDENTITY', 'PROFESSIONAL')),
    CONSTRAINT verification_state_known
        CHECK (state IN ('SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE UNIQUE INDEX verification_one_open_per_kind
    ON verification_request (user_id, kind)
    WHERE state IN ('SUBMITTED', 'IN_REVIEW');

CREATE INDEX verification_queue_idx ON verification_request (state, submitted_at);
CREATE INDEX verification_user_idx  ON verification_request (user_id, submitted_at DESC);

CREATE TABLE professional_profile (
    user_id          uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    trade            text        NOT NULL,
    business_name    text,
    about            text,
    -- A published business address: exact on purpose, unlike a home point.
    shop_address     text,
    shop_lat         double precision,
    shop_lon         double precision,
    shop_point       geography(Point, 4326) GENERATED ALWAYS AS (
                         CASE WHEN shop_lat IS NULL OR shop_lon IS NULL THEN NULL
                              ELSE ST_SetSRID(ST_MakePoint(shop_lon, shop_lat), 4326)::geography END
                     ) STORED,
    -- How far they are willing to travel for a call-out.
    service_radius_km int        NOT NULL DEFAULT 10,
    years_experience int,
    licence_ref      text,
    insurance_ref    text,
    languages        text,
    contact_phone    text,
    -- PENDING until an admin approves the PROFESSIONAL request; only ACTIVE
    -- profiles may publish a home-visit listing.
    state            text        NOT NULL DEFAULT 'PENDING',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT professional_state_known CHECK (state IN ('PENDING', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT professional_radius_sane CHECK (service_radius_km BETWEEN 1 AND 100)
);

CREATE INDEX professional_shop_gix   ON professional_profile USING gist (shop_point);
CREATE INDEX professional_trade_idx  ON professional_profile (trade) WHERE state = 'ACTIVE';

-- The ID check is a property of the person, so it lives on the profile next to
-- the rating. `verified` already existed; this records when and by what.
ALTER TABLE profile ADD COLUMN verified_at   timestamptz;
ALTER TABLE profile ADD COLUMN verified_by   text;
ALTER TABLE profile ADD COLUMN professional  boolean NOT NULL DEFAULT false;
