package com.kora.ecommerce.auditnotification.persistence;

public record AuditEventSource(
        String topic,
        int partition,
        long offset) {

    public AuditEventSource {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        topic = topic.trim();
        if (partition < 0) {
            throw new IllegalArgumentException("partition must not be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    public AuditEventSource(String topic, Integer partition, Long offset) {
        this(
                topic,
                requireHeader(partition, "partition"),
                requireHeader(offset, "offset"));
    }

    private static int requireHeader(Integer value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " header is required");
        }
        return value;
    }

    private static long requireHeader(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " header is required");
        }
        return value;
    }
}
