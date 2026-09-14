-- Radius trades in rupees. The euro default was a placeholder from Phase 01 and
-- never a decision.
--
-- Nothing is rescaled: amounts stay integer minor units, and the minor unit is
-- 100 to the major one for both currencies, so a paise column and a cent column
-- hold the same integers. Only the code attached to them changes.

ALTER TABLE listing ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE listing SET currency = 'INR' WHERE currency = 'EUR';
