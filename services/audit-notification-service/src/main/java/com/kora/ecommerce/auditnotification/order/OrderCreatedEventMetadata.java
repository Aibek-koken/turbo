package com.kora.ecommerce.auditnotification.order;

public record OrderCreatedEventMetadata(
        String eventId,
        String eventType,
        Integer eventVersion,
        String aggregateId) {

    public static OrderCreatedEventMetadata empty() {
        return new OrderCreatedEventMetadata(null, null, null, null);
    }
}
