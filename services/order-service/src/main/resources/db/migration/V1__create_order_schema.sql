CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subtotal_amount NUMERIC(19, 4) NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT orders_status_check CHECK (
        status IN ('CREATED', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'CANCELLED')
    ),
    CONSTRAINT orders_subtotal_non_negative_check CHECK (subtotal_amount >= 0),
    CONSTRAINT orders_total_non_negative_check CHECK (total_amount >= 0),
    CONSTRAINT orders_v1_total_matches_subtotal_check CHECK (total_amount = subtotal_amount),
    CONSTRAINT orders_currency_check CHECK (length(currency) = 3 AND currency = upper(currency)),
    CONSTRAINT orders_version_non_negative_check CHECK (version >= 0)
);

CREATE INDEX idx_orders_customer_created_at ON orders (customer_id, created_at);
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    item_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    product_sku VARCHAR(128) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    line_total_amount NUMERIC(19, 4) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT uq_order_items_order_item_number UNIQUE (order_id, item_number),
    CONSTRAINT uq_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT order_items_item_number_positive_check CHECK (item_number > 0),
    CONSTRAINT order_items_quantity_positive_check CHECK (quantity > 0),
    CONSTRAINT order_items_unit_price_non_negative_check CHECK (unit_price_amount >= 0),
    CONSTRAINT order_items_line_total_non_negative_check CHECK (line_total_amount >= 0),
    CONSTRAINT order_items_line_total_matches_quantity_check CHECK (line_total_amount = unit_price_amount * quantity),
    CONSTRAINT order_items_currency_check CHECK (length(currency) = 3 AND currency = upper(currency))
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(500),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_status_history_status_check CHECK (
        status IN ('CREATED', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'CANCELLED')
    )
);

CREATE INDEX idx_order_status_history_order_changed_at ON order_status_history (order_id, changed_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trace_id VARCHAR(128),
    correlation_id VARCHAR(128),
    payload JSON NOT NULL,
    CONSTRAINT outbox_events_event_version_positive_check CHECK (event_version >= 1),
    CONSTRAINT outbox_events_aggregate_type_not_blank_check CHECK (length(trim(aggregate_type)) > 0),
    CONSTRAINT outbox_events_event_type_not_blank_check CHECK (length(trim(event_type)) > 0)
);

CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id, occurred_at);
CREATE INDEX idx_outbox_events_event_type_occurred_at ON outbox_events (event_type, occurred_at);
