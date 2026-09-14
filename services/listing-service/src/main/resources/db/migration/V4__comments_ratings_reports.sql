-- Block A: the moderation plumbing.
--
-- Comments, ratings, reports and comment bans all land in listing-service
-- because every one of them hangs off a listing, and splitting them across
-- services would mean a distributed transaction to delete a listing.

-- ---------------------------------------------------------------- comments

CREATE TABLE listing_comment (
    id          uuid        PRIMARY KEY,
    listing_id  uuid        NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    author_id   uuid        NOT NULL,
    -- One level of threading only. A reply to a reply is a UI problem nobody
    -- wants, so the service refuses to nest deeper than this column allows.
    parent_id   uuid        REFERENCES listing_comment (id) ON DELETE CASCADE,
    body        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    edited_at   timestamptz,
    -- Soft delete: the row stays, the body stops being served. A comment that
    -- vanishes entirely leaves no evidence when its author is reported again.
    deleted_at  timestamptz,
    deleted_by  uuid
);

CREATE INDEX listing_comment_listing_idx ON listing_comment (listing_id, created_at);
CREATE INDEX listing_comment_author_idx  ON listing_comment (author_id);
CREATE INDEX listing_comment_parent_idx  ON listing_comment (parent_id) WHERE parent_id IS NOT NULL;

-- ----------------------------------------------------------------- ratings

CREATE TABLE listing_rating (
    id          uuid        PRIMARY KEY,
    listing_id  uuid        NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    author_id   uuid        NOT NULL,
    stars       smallint    NOT NULL CHECK (stars BETWEEN 1 AND 5),
    title       text,
    body        text,
    -- Set when the rating came from someone who actually completed a booking.
    -- Anyone may rate, but this is what earns the "verified booking" badge and
    -- sorts first — Amazon's middle path, so the page is useful on day one
    -- without being worthless.
    request_id  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    edited_at   timestamptz,
    deleted_at  timestamptz,
    deleted_by  uuid
);

-- One rating per person per listing. Changing your mind edits the existing row.
CREATE UNIQUE INDEX listing_rating_one_per_author ON listing_rating (listing_id, author_id);
CREATE INDEX listing_rating_listing_idx ON listing_rating (listing_id, created_at);

-- Denormalised onto the listing so a feed card renders without a join or a
-- count. Maintained by RatingService on every write; a Kafka Streams job is the
-- eventual owner of the equivalent numbers on profile.
ALTER TABLE listing ADD COLUMN rating_avg   numeric(3,2);
ALTER TABLE listing ADD COLUMN rating_count int NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------- reports

-- One table, not three. target_type + target_id costs nothing now and saves a
-- migration every time something new becomes reportable.
CREATE TABLE report (
    id           uuid        PRIMARY KEY,
    reporter_id  uuid        NOT NULL,
    target_type  text        NOT NULL CHECK (target_type IN ('LISTING', 'COMMENT', 'RATING')),
    target_id    uuid        NOT NULL,
    reason       text        NOT NULL,
    detail       text,
    state        text        NOT NULL DEFAULT 'OPEN'
                             CHECK (state IN ('OPEN', 'REVIEWING', 'ACTIONED', 'DISMISSED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    decided_by   uuid,
    decided_at   timestamptz,
    note         text
);

CREATE INDEX report_state_idx  ON report (state, created_at);
CREATE INDEX report_target_idx ON report (target_type, target_id);

-- The same person reporting the same thing twice is one report, not two.
CREATE UNIQUE INDEX report_one_per_reporter
    ON report (reporter_id, target_type, target_id);

-- ------------------------------------------------------------ comment bans

-- Deliberately its own table rather than a column on member_location:
-- member_location is a projection of radius.user.v1 and is never written from a
-- request handler. This is locally owned moderation state, so it lives apart.
CREATE TABLE comment_ban (
    user_id      uuid        PRIMARY KEY,
    -- A timestamp, not a boolean. An admin sets a duration and the ban lifts
    -- itself; nobody has to remember to undo it.
    banned_until timestamptz NOT NULL,
    reason       text        NOT NULL,
    set_by       uuid        NOT NULL,
    set_at       timestamptz NOT NULL DEFAULT now()
);

-- -------------------------------------------------- who actually transacted

-- A projection of RequestCompleted on radius.request.v1. It exists so a rating
-- can be badged "verified booking" without listing-service calling
-- booking-service on every write — and so the badge survives booking-service
-- being down.
CREATE TABLE completed_booking (
    request_id   uuid        PRIMARY KEY,
    listing_id   uuid        NOT NULL,
    user_id      uuid        NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX completed_booking_who_idx ON completed_booking (listing_id, user_id);

-- Age of the account, mirrored from UserRegistered. Comments and ratings are
-- what gets abused first, and the tighter bucket a new account gets needs to
-- know how new it is. Null means "we never saw the registration" — treated as
-- established, because every member from before this migration is.
ALTER TABLE member_location ADD COLUMN registered_at timestamptz;
