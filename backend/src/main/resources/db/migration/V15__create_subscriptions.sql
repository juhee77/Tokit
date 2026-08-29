-- V15__create_subscriptions.sql
-- Description: Record each public-offering subscription so investor limits can be
--              evaluated per issuer and per year. Wallet balances alone cannot answer
--              "how much did this investor commit to this issuer this year", which the
--              조각투자 가이드라인 requires before accepting a subscription.

CREATE TABLE subscriptions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users (id),
    asset_id       BIGINT         NOT NULL REFERENCES assets (id),
    issuer_id      BIGINT         NOT NULL REFERENCES issuers (id),
    amount         NUMERIC(20, 4) NOT NULL,
    token_quantity NUMERIC(20, 4) NOT NULL,
    subscribed_at  TIMESTAMP      NOT NULL
);

CREATE INDEX idx_subscriptions_user_subscribed_at ON subscriptions (user_id, subscribed_at);
CREATE INDEX idx_subscriptions_user_issuer ON subscriptions (user_id, issuer_id);
