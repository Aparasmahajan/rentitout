-- The inbox. Every event that wants to reach a human lands here first, then
-- goes out over whichever channel the member has enabled.

CREATE TABLE notification (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    kind       text        NOT NULL,
    title      text        NOT NULL,
    body       text,
    deep_link  text,
    channel    text        NOT NULL DEFAULT 'inbox',   -- inbox | push | sms | email
    state      text        NOT NULL DEFAULT 'NEW',     -- NEW | SENT | READ | FAILED
    event_id   text,                                   -- the outbox row that produced it
    created_at timestamptz NOT NULL DEFAULT now(),
    read_at    timestamptz
);

CREATE INDEX notification_user_idx ON notification (user_id, created_at DESC);
CREATE INDEX notification_unread_idx ON notification (user_id) WHERE read_at IS NULL;
-- The same event must never produce two notifications, however often Kafka
-- redelivers it.
CREATE UNIQUE INDEX notification_event_uidx ON notification (event_id) WHERE event_id IS NOT NULL;

CREATE TABLE device_token (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    platform   text        NOT NULL,        -- ios | android | web
    token      text UNIQUE NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX device_token_user_idx ON device_token (user_id);
