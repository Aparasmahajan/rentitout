-- A listing that puts someone in a stranger's home is a different risk from
-- lending a ladder, so it is marked as such and gated on the owner's checks.

ALTER TABLE listing ADD COLUMN home_visit boolean NOT NULL DEFAULT false;
CREATE INDEX listing_home_visit_idx ON listing (home_visit) WHERE status = 'LIVE';

-- Mirrored from radius.user.v1 so this service can enforce the rule and render
-- the badge without asking user-service on every request.
ALTER TABLE member_location ADD COLUMN id_checked   boolean NOT NULL DEFAULT false;
ALTER TABLE member_location ADD COLUMN professional boolean NOT NULL DEFAULT false;
ALTER TABLE member_location ADD COLUMN trade        text;
