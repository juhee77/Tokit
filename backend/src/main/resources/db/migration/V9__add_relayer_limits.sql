ALTER TABLE relayer_nonces ADD COLUMN last_tx_date DATE;
ALTER TABLE relayer_nonces ADD COLUMN daily_tx_count INT NOT NULL DEFAULT 0;
