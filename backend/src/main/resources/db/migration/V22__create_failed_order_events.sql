-- V22__create_failed_order_events.sql
-- Description: Keep order events that were dead-lettered after matching retries (#23).
--              The DLQ consumer acknowledges the message after alerting, so without this
--              table a missed or failed Slack alert leaves no trace of an order whose
--              funds are still held. Admins replay or cancel from these rows.

CREATE TABLE failed_order_events (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT         NOT NULL,
    user_id          BIGINT,
    asset_symbol     VARCHAR(255),
    order_type       VARCHAR(255),
    price            NUMERIC(20, 4),
    quantity         NUMERIC(20, 4),
    status           VARCHAR(255)   NOT NULL,
    resolution       VARCHAR(255),
    failure_count    INTEGER        NOT NULL,
    replay_count     INTEGER        NOT NULL,
    first_failed_at  TIMESTAMP      NOT NULL,
    last_failed_at   TIMESTAMP      NOT NULL,
    last_replayed_at TIMESTAMP,
    resolved_at      TIMESTAMP
);

CREATE INDEX idx_failed_order_events_order ON failed_order_events (order_id);
CREATE INDEX idx_failed_order_events_status ON failed_order_events (status, last_failed_at);
