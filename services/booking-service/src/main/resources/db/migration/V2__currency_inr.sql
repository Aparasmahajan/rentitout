-- See listing-service V3. Requests carry the currency they were priced in, so
-- historical rows are rewritten too — a POC seed has no real euro bookings to
-- preserve, and leaving a mix would make the breakdown render two symbols.

ALTER TABLE booking_request ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE booking_request SET currency = 'INR' WHERE currency = 'EUR';
