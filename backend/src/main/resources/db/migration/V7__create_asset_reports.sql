CREATE TABLE asset_reports (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_reports_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE
);
