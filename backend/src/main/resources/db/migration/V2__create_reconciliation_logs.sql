-- V2__create_reconciliation_logs.sql
-- Description: Add reconciliation_logs table for on-off chain balance daily audits

CREATE TABLE reconciliation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    wallet_address VARCHAR(255) NOT NULL,
    offchain_balance NUMERIC(20, 4) NOT NULL,
    onchain_balance NUMERIC(20, 4) NOT NULL,
    difference NUMERIC(20, 4) NOT NULL,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reconciliation_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reconciliation_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);
