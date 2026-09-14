-- See listing-service V3.
--
-- Both money tables move together. A payout aggregates payments, so a payout in
-- one currency over payments in another would be wrong rather than merely ugly.

ALTER TABLE payment ALTER COLUMN currency SET DEFAULT 'INR';
ALTER TABLE payout  ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE payment SET currency = 'INR' WHERE currency = 'EUR';
UPDATE payout  SET currency = 'INR' WHERE currency = 'EUR';
