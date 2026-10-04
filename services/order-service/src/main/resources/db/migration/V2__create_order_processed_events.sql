CREATE TABLE processed_events (
    consumer_name VARCHAR(128) NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    CONSTRAINT processed_events_consumer_name_not_blank_check CHECK (length(trim(consumer_name)) > 0),
    CONSTRAINT processed_events_event_type_check CHECK (event_type IN ('PaymentSucceeded', 'PaymentFailed'))
);

CREATE INDEX idx_processed_events_aggregate ON processed_events (aggregate_id, processed_at);
CREATE INDEX idx_processed_events_payment ON processed_events (payment_id, processed_at);
