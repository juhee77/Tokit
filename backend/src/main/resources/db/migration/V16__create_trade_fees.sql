-- V16__create_trade_fees.sql
-- Description: Ledger for secondary-market trading fees. One row per side of a trade,
--              storing the rate in effect at the time so historical settlements stay
--              reconstructable after the fee schedule changes.

CREATE TABLE trade_fees (
    id             BIGSERIAL PRIMARY KEY,
    trade_id       BIGINT          NOT NULL REFERENCES trades (id),
    user_id        BIGINT          NOT NULL REFERENCES users (id),
    side           VARCHAR(10)     NOT NULL,
    taxable_amount NUMERIC(20, 4)  NOT NULL,
    fee_amount     NUMERIC(20, 4)  NOT NULL,
    fee_rate       NUMERIC(10, 6)  NOT NULL,
    charged_at     TIMESTAMP       NOT NULL
);

CREATE INDEX idx_trade_fees_trade ON trade_fees (trade_id);
CREATE INDEX idx_trade_fees_user_charged_at ON trade_fees (user_id, charged_at);
