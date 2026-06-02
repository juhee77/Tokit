-- V1__init_schema.sql
-- Description: TOKIT STO Core Database Schema
-- Dialect: PostgreSQL

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    kyc_status BOOLEAN NOT NULL DEFAULT FALSE,
    email VARCHAR(255) NOT NULL UNIQUE,
    wallet_address VARCHAR(255) NOT NULL
);

CREATE TABLE issuers (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    biz_reg_no VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    issuer_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    total_supply NUMERIC(20, 4) NOT NULL,
    issue_price NUMERIC(20, 4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    symbol VARCHAR(50) NOT NULL UNIQUE,
    contract_address VARCHAR(255) NOT NULL,
    CONSTRAINT fk_assets_issuer FOREIGN KEY (issuer_id) REFERENCES issuers(id)
);

CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT, -- NULL for KRW (Fiat)
    balance NUMERIC(20, 4) NOT NULL DEFAULT 0,
    locked_balance NUMERIC(20, 4) NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_wallets_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL, -- BUY/SELL
    price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(20, 4) NOT NULL,
    remain_qty NUMERIC(20, 4) NOT NULL,
    status VARCHAR(20) NOT NULL, -- OPEN/PARTIAL/FILLED/CANCELED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    buy_order_id BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(20, 4) NOT NULL,
    traded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trades_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(id),
    CONSTRAINT fk_trades_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(id),
    CONSTRAINT fk_trades_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);
