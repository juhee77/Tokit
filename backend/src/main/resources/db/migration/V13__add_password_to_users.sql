-- V13__add_password_to_users.sql
-- Description: Introduce credential storage so API access can be authenticated
--              instead of trusting a client-supplied userId.
--
-- Existing rows (seeded demo/test accounts) are backfilled with the BCrypt hash
-- of the shared development password 'tokit1234' so local environments keep working.
-- These are demo credentials only and must be rotated before any real deployment.

ALTER TABLE users ADD COLUMN password VARCHAR(100);

UPDATE users
   SET password = '$2a$10$jmR7aODrJzBiQ29S/RQMzuNC8JGAahq046HTQZjBrA06XYfeaF8yu'
 WHERE password IS NULL;

ALTER TABLE users ALTER COLUMN password SET NOT NULL;
