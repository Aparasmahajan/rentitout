-- See listing-service V3.
--
-- listing_doc is a projection, so the durable fix is upstream: a republished
-- listing arrives carrying INR and the upsert corrects the row. This UPDATE is
-- for the rows already sitting here — without it, seeded listings would keep
-- rendering a euro sign on the map and in search results until something
-- happened to touch them.

ALTER TABLE listing_doc ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE listing_doc SET currency = 'INR' WHERE currency = 'EUR';
