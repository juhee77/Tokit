CREATE TABLE relayer_nonces (
    wallet_address VARCHAR(255) PRIMARY KEY,
    next_nonce BIGINT NOT NULL DEFAULT 0
);
