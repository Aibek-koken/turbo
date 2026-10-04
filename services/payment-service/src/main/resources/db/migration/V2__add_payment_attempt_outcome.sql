ALTER TABLE payment_attempts
    ADD COLUMN outcome VARCHAR(32) NOT NULL DEFAULT 'REQUESTED';

ALTER TABLE payment_attempts
    ADD CONSTRAINT ck_payment_attempts_outcome
        CHECK (outcome IN ('REQUESTED', 'SUCCEEDED', 'DECLINED', 'TIMED_OUT', 'PROVIDER_5XX', 'MALFORMED_RESPONSE'));

CREATE INDEX idx_payment_attempts_outcome_created_at
    ON payment_attempts (outcome, created_at DESC, id DESC);
