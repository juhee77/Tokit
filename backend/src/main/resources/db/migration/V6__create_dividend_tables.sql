-- V6__create_dividend_tables.sql
-- Description: Schema for STO Dividend Payout Batch
-- Dialect: PostgreSQL

CREATE TABLE dividend_payouts (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    total_dividend_amount NUMERIC(20, 4) NOT NULL,
    payout_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_dividend_payouts_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE TABLE dividend_payout_details (
    id BIGSERIAL PRIMARY KEY,
    payout_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    wallet_address VARCHAR(255) NOT NULL,
    share_ratio NUMERIC(10, 6) NOT NULL,
    payout_amount NUMERIC(20, 4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message VARCHAR(500),
    CONSTRAINT fk_details_payout FOREIGN KEY (payout_id) REFERENCES dividend_payouts(id),
    CONSTRAINT fk_details_user FOREIGN KEY (user_id) REFERENCES users(id)
);
