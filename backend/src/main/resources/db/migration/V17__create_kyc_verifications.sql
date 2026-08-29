-- V17__create_kyc_verifications.sql
-- Description: Preserve the outcome and evidence of each identity-verification attempt.
--              특정금융정보법 requires retaining customer due-diligence records; a single
--              boolean on users cannot say who approved a customer, when, or on what basis.

CREATE TABLE kyc_verifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    status              VARCHAR(20) NOT NULL,
    provider            VARCHAR(50) NOT NULL,
    provider_reference  VARCHAR(255),
    reject_reason       VARCHAR(500),
    verified_at         TIMESTAMP   NOT NULL
);

CREATE INDEX idx_kyc_verifications_user_verified_at ON kyc_verifications (user_id, verified_at);
