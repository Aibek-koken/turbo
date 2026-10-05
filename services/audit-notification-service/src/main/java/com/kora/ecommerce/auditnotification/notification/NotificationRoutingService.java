package com.kora.ecommerce.auditnotification.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "audit-notification.mongodb.repositories",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationRoutingService {

    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final Set<String> CUSTOMER_NOTIFICATION_EVENT_TYPES = Set.of(
            "OrderCreated",
            "PaymentSucceeded",
            "PaymentFailed");
    private static final List<NotificationChannel> CUSTOMER_CHANNELS = List.of(
            NotificationChannel.EMAIL,
            NotificationChannel.PUSH);
    private static final Map<NotificationDeliveryStatus, NotificationRoutingResult.ChannelOutcome>
            EXISTING_OUTCOMES = existingOutcomes();

    private final NotificationDeliveryRepository repository;
    private final Clock clock;

    public NotificationRoutingService(NotificationDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public NotificationRoutingResult routeOrderCreated(AuditOrderCreatedEvent event) {
        return route(NotificationRouteRequest.from(event));
    }

    public NotificationRoutingResult routePaymentResult(AuditPaymentResultEvent event) {
        return route(NotificationRouteRequest.from(event));
    }

    public NotificationRoutingResult route(NotificationRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!isCustomerNotificationEvent(request)) {
            return NotificationRoutingResult.ignored(request);
        }

        Instant now = Instant.now(clock);
        List<NotificationRoutingResult.ChannelDeliveryResult> results = new ArrayList<>();
        for (NotificationChannel channel : CUSTOMER_CHANNELS) {
            results.add(ensureDeliveryRecord(request, channel, now));
        }
        return NotificationRoutingResult.routed(request, results);
    }

    private boolean isCustomerNotificationEvent(NotificationRouteRequest request) {
        return request.eventVersion() == SUPPORTED_EVENT_VERSION
                && CUSTOMER_NOTIFICATION_EVENT_TYPES.contains(request.eventType());
    }

    private NotificationRoutingResult.ChannelDeliveryResult ensureDeliveryRecord(
            NotificationRouteRequest request,
            NotificationChannel channel,
            Instant now) {
        Optional<NotificationDeliveryDocument> existing =
                repository.findByEventIdAndChannel(request.eventId(), channel);
        if (existing.isPresent()) {
            return handleExisting(existing.get(), channel, now);
        }

        try {
            repository.save(NotificationDeliveryDocument.pending(request, channel, now));
            return new NotificationRoutingResult.ChannelDeliveryResult(
                    channel,
                    NotificationRoutingResult.ChannelOutcome.CREATED);
        } catch (DuplicateKeyException exception) {
            return repository.findByEventIdAndChannel(request.eventId(), channel)
                    .map(document -> handleExisting(document, channel, now))
                    .orElseThrow(() -> exception);
        }
    }

    private NotificationRoutingResult.ChannelDeliveryResult handleExisting(
            NotificationDeliveryDocument document,
            NotificationChannel channel,
            Instant now) {
        NotificationDeliveryStatus originalStatus = document.getStatus();
        if (document.isRetryableForRoutingReplay()) {
            document.markPendingForRetry(now);
            repository.save(document);
        }
        return new NotificationRoutingResult.ChannelDeliveryResult(
                channel,
                EXISTING_OUTCOMES.get(originalStatus));
    }

    private static Map<NotificationDeliveryStatus, NotificationRoutingResult.ChannelOutcome> existingOutcomes() {
        Map<NotificationDeliveryStatus, NotificationRoutingResult.ChannelOutcome> outcomes =
                new EnumMap<>(NotificationDeliveryStatus.class);
        outcomes.put(
                NotificationDeliveryStatus.PENDING,
                NotificationRoutingResult.ChannelOutcome.ALREADY_PENDING);
        outcomes.put(
                NotificationDeliveryStatus.IN_PROGRESS,
                NotificationRoutingResult.ChannelOutcome.REQUEUED_IN_PROGRESS);
        outcomes.put(
                NotificationDeliveryStatus.SENT,
                NotificationRoutingResult.ChannelOutcome.ALREADY_SENT);
        outcomes.put(
                NotificationDeliveryStatus.FAILED,
                NotificationRoutingResult.ChannelOutcome.REQUEUED_FAILED);
        return Map.copyOf(outcomes);
    }
}
