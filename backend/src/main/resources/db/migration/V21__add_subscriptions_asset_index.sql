-- V21__add_subscriptions_asset_index.sql
-- Description: Subscriptions are now checked against the asset's total supply on every
--              request (#30). The check sums token_quantity per asset while holding the
--              asset row lock, so it must not scan the whole table.

CREATE INDEX idx_subscriptions_asset ON subscriptions (asset_id);
