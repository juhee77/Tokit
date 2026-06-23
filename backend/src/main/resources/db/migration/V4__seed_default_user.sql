-- V4__seed_default_user.sql
-- Description: Seed a default testing user (ID=1) with KRW and HDYT token balances

-- Clear existing test investor to prevent email conflicts
DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email = 'test-investor@tokit.com');
DELETE FROM users WHERE email = 'test-investor@tokit.com';

-- Insert default user (ID=1)
INSERT INTO users (id, name, kyc_status, email, wallet_address)
VALUES (
    1,
    '김토킷',
    true,
    'test-investor@tokit.com',
    '0x70997970C51812dc3A010C7d01b50e0d17dc79C8'
)
ON CONFLICT (id) DO NOTHING;

-- Adjust sequence to prevent future primary key conflicts
SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1) + 1000);

-- Insert KRW wallet (fiat) for default user
INSERT INTO wallets (user_id, asset_id, balance, locked_balance, version)
VALUES (
    1,
    NULL,
    10000000.0000,
    0.0000,
    0
)
ON CONFLICT DO NOTHING;

-- Insert HDYT token wallet for default user (so they have some tokens to sell/trade)
INSERT INTO wallets (user_id, asset_id, balance, locked_balance, version)
VALUES (
    1,
    (SELECT id FROM assets WHERE symbol = 'HDYT'),
    1000.0000,
    0.0000,
    0
)
ON CONFLICT DO NOTHING;
