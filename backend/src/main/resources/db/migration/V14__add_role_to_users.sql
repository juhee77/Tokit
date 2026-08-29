-- V14__add_role_to_users.sql
-- Description: Distinguish operator accounts from investors so administrative
--              endpoints (asset registration, dividend runs, reconciliation logs,
--              KYC overrides) can be restricted by role instead of being open to
--              any authenticated caller.

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 로컬/데모 환경의 기본 계정을 운영자로 승격해 어드민 화면이 계속 동작하게 합니다.
UPDATE users SET role = 'ADMIN' WHERE email = 'test-investor@tokit.com';
