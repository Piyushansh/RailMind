package com.railmind.audit;

import com.railmind.events.DomainEventPublisher;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Append-only audit trail. Every sensitive action (auth, booking, payment,
 * cancellation, refund, AI tool execution) should call this so behaviour is
 * reconstructable after the fact.
 *
 * Every audit entry is also published to the audit-events Kafka topic
 * (fire-and-forget - see DomainEventPublisher), giving audit-events 100%
 * real coverage of everything this service ever records, not a decorative
 * subset. Entries whose action name matches a booking- or payment-flavored
 * keyword (BOOKING_CONFIRMED, PAYMENT_SUCCESS, REBOOKING_APPROVED, etc. -
 * see the real action names already used throughout BookingService,
 * PaymentService, CancellationService, RebookingService) are ADDITIONALLY
 * published to booking-events/payment-events. This routes those two topics
 * off the one call site every relevant service already reliably reaches,
 * rather than threading DomainEventPublisher through BookingService's and
 * PaymentService's own already-large, already-tested constructors for the
 * same observability purpose.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final DomainEventPublisher eventPublisher;
    private final String bookingEventsTopic;
    private final String paymentEventsTopic;
    private final String auditEventsTopic;

    public AuditService(AuditLogRepository repository, DomainEventPublisher eventPublisher,
                         @Value("${railmind.kafka.booking-events-topic}") String bookingEventsTopic,
                         @Value("${railmind.kafka.payment-events-topic}") String paymentEventsTopic,
                         @Value("${railmind.kafka.audit-events-topic}") String auditEventsTopic) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.bookingEventsTopic = bookingEventsTopic;
        this.paymentEventsTopic = paymentEventsTopic;
        this.auditEventsTopic = auditEventsTopic;
    }

    public void log(Long actorUserId, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        // Picked up automatically when set (e.g. TrainEventConsumer sets "correlationId" in MDC
        // for the duration of processing one Kafka event) - callers never need to pass this
        // explicitly, so every existing call site keeps working unchanged.
        log.setCorrelationId(MDC.get("correlationId"));
        repository.save(log);

        Map<String, Object> payload = Map.of(
                "actorUserId", actorUserId != null ? actorUserId : 0L,
                "action", action,
                "entityType", entityType != null ? entityType : "",
                "entityId", entityId != null ? entityId : 0L,
                "details", details != null ? details : "");
        eventPublisher.publish(auditEventsTopic, action, payload);

        String upper = action.toUpperCase();
        if (upper.contains("BOOKING") || upper.contains("REBOOKING") || upper.contains("TRIP_GUARDIAN")) {
            eventPublisher.publish(bookingEventsTopic, action, payload);
        }
        if (upper.contains("PAYMENT") || upper.contains("REFUND")) {
            eventPublisher.publish(paymentEventsTopic, action, payload);
        }
    }
}
