package com.kora.ecommerce.auditnotification.order;

public class InvalidOrderCreatedEventException extends RuntimeException {

    private final String reason;
    private final OrderCreatedEventMetadata metadata;

    public InvalidOrderCreatedEventException(String reason, OrderCreatedEventMetadata metadata) {
        super("Invalid OrderCreated audit event: " + reason);
        this.reason = reason;
        this.metadata = metadata == null ? OrderCreatedEventMetadata.empty() : metadata;
    }

    public InvalidOrderCreatedEventException(
            String reason,
            OrderCreatedEventMetadata metadata,
            Throwable cause) {
        super("Invalid OrderCreated audit event: " + reason, cause);
        this.reason = reason;
        this.metadata = metadata == null ? OrderCreatedEventMetadata.empty() : metadata;
    }

    public String reason() {
        return reason;
    }

    public OrderCreatedEventMetadata metadata() {
        return metadata;
    }
}
