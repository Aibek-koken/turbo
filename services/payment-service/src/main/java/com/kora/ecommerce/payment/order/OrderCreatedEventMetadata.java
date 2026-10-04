package com.kora.ecommerce.payment.order;

public record OrderCreatedEventMetadata(
        String eventId,
        String eventType,
        Integer eventVersion,
        String aggregateId) {

    static OrderCreatedEventMetadata empty() {
        return new OrderCreatedEventMetadata(null, null, null, null);
    }
}
