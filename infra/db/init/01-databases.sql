-- Database per service. In production these are separate instances (or at
-- least separate clusters); locally they are separate databases on one server
-- so the isolation rule still holds: no service reads another service's tables.

CREATE DATABASE radius_user;
CREATE DATABASE radius_listing;
CREATE DATABASE radius_search;
CREATE DATABASE radius_booking;
CREATE DATABASE radius_payment;
CREATE DATABASE radius_notification;

\connect radius_user
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

\connect radius_listing
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

\connect radius_search
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

\connect radius_booking
CREATE EXTENSION IF NOT EXISTS postgis;

\connect radius_payment
-- no spatial data here

\connect radius_notification
-- no spatial data here
