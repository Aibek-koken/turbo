package com.kora.ecommerce.auditnotification.notification;

import java.util.List;

public record NotificationRoutingResult(
        String eventId,
        String eventType,
        Outcome outcome,
        List<ChannelDeliveryResult> channels) {

    public NotificationRoutingResult {
        channels = List.copyOf(channels);
    }

    static NotificationRoutingResult ignored(NotificationRouteRequest request) {
        return new NotificationRoutingResult(
                request.eventId(),
                request.eventType(),
                Outcome.IGNORED,
                List.of());
    }

    static NotificationRoutingResult routed(
            NotificationRouteRequest request,
            List<ChannelDeliveryResult> channels) {
        return new NotificationRoutingResult(
                request.eventId(),
                request.eventType(),
                Outcome.ROUTED,
                channels);
    }

    public enum Outcome {
        ROUTED,
        IGNORED
    }

    public record ChannelDeliveryResult(
            NotificationChannel channel,
            ChannelOutcome outcome) {
    }

    public enum ChannelOutcome {
        CREATED,
        ALREADY_PENDING,
        REQUEUED_IN_PROGRESS,
        ALREADY_SENT,
        REQUEUED_FAILED
    }
}
