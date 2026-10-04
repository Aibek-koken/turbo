CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(128),
    failure_reason VARCHAR(512),
    source_event_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payments_customer_id_not_blank CHECK (TRIM(customer_id) <> ''),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payments_customer_created_at
    ON payments (customer_id, created_at DESC, id DESC);

CREATE INDEX idx_payments_status_created_at
    ON payments (status, created_at DESC);

CREATE INDEX idx_payments_source_event_id
    ON payments (source_event_id);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_request_id UUID,
    provider_reference VARCHAR(128),
    failure_reason VARCHAR(512),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT uk_payment_attempts_payment_attempt_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT uk_payment_attempts_provider_request_id UNIQUE (provider_request_id),
    CONSTRAINT ck_payment_attempts_attempt_number_positive CHECK (attempt_number > 0),
    CONSTRAINT ck_payment_attempts_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payment_attempts_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_payment_attempts_payment_requested_at
    ON payment_attempts (payment_id, requested_at DESC, id DESC);

CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_id UUID NOT NULL,
    trace_id VARCHAR(64),
    correlation_id VARCHAR(128),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_events_consumer_event_id UNIQUE (consumer_name, event_id),
    CONSTRAINT ck_processed_events_event_version_positive CHECK (event_version > 0),
    CONSTRAINT ck_processed_events_consumer_name_not_blank CHECK (TRIM(consumer_name) <> ''),
    CONSTRAINT ck_processed_events_event_type_not_blank CHECK (TRIM(event_type) <> '')
);

CREATE INDEX idx_processed_events_aggregate_processed_at
    ON processed_events (aggregate_id, processed_at DESC, id DESC);

CREATE INDEX idx_processed_events_type_processed_at
    ON processed_events (event_type, processed_at DESC, id DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trace_id VARCHAR(64),
    correlation_id VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_outbox_events_event_version_positive CHECK (event_version > 0),
    CONSTRAINT ck_outbox_events_event_type_not_blank CHECK (TRIM(event_type) <> '')
);

CREATE INDEX idx_outbox_events_aggregate_occurred_at
    ON outbox_events (aggregate_id, occurred_at ASC, id ASC);

CREATE INDEX idx_outbox_events_type_created_at
    ON outbox_events (event_type, created_at ASC, id ASC);
