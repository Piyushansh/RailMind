package com.railmind.audit;

import com.railmind.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers Phase 2's "audit logs must store correlation_id when available"
 * and "missing correlation IDs must not cause failures" requirements, plus
 * Phase 7's audit-events/booking-events/payment-events routing: every
 * audit entry always publishes to audit-events, and additionally to
 * booking-events or payment-events when the action name matches (see
 * AuditService's own Javadoc for why routing happens here rather than in
 * each individual business service).
 */
class AuditServiceTest {

    private AuditService newService(AuditLogRepository repository, DomainEventPublisher eventPublisher) {
        return new AuditService(repository, eventPublisher, "booking-events", "payment-events", "audit-events");
    }

    @Test
    void persistsCorrelationId_whenPresentInMdc() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditService auditService = newService(repository, mock(DomainEventPublisher.class));

        MDC.put("correlationId", "req-abc-123");
        try {
            auditService.log(1L, "TEST_ACTION", "Thing", 5L, "details");
        } finally {
            MDC.remove("correlationId");
        }

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("req-abc-123");
    }

    @Test
    void doesNotFail_whenNoCorrelationIdIsPresent() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditService auditService = newService(repository, mock(DomainEventPublisher.class));

        MDC.clear();
        auditService.log(1L, "TEST_ACTION", "Thing", 5L, "details");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isNull();
    }

    @Test
    void everyAuditEntry_isPublishedToAuditEventsTopic() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        AuditService auditService = newService(repository, eventPublisher);

        auditService.log(1L, "SOME_UNRELATED_ACTION", "Thing", 5L, "details");

        verify(eventPublisher).publish(eq("audit-events"), eq("SOME_UNRELATED_ACTION"), any());
    }

    @Test
    void bookingFlavoredAction_isAlsoPublishedToBookingEvents() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        AuditService auditService = newService(repository, eventPublisher);

        auditService.log(1L, "BOOKING_CONFIRMED", "Booking", 5L, "PNR123");

        verify(eventPublisher).publish(eq("audit-events"), eq("BOOKING_CONFIRMED"), any());
        verify(eventPublisher).publish(eq("booking-events"), eq("BOOKING_CONFIRMED"), any());
        verify(eventPublisher, never()).publish(eq("payment-events"), any(), any());
    }

    @Test
    void paymentFlavoredAction_isAlsoPublishedToPaymentEvents() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        AuditService auditService = newService(repository, eventPublisher);

        auditService.log(1L, "PAYMENT_SUCCESS", "Payment", 5L, "booking=9");

        verify(eventPublisher).publish(eq("audit-events"), eq("PAYMENT_SUCCESS"), any());
        verify(eventPublisher).publish(eq("payment-events"), eq("PAYMENT_SUCCESS"), any());
        verify(eventPublisher, never()).publish(eq("booking-events"), any(), any());
    }

    @Test
    void unrelatedAction_isNeverPublishedToBookingOrPaymentEvents() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        AuditService auditService = newService(repository, eventPublisher);

        auditService.log(1L, "ADMIN_ACTIVATED_STATION", "Station", 5L, null);

        verify(eventPublisher, never()).publish(eq("booking-events"), any(), any());
        verify(eventPublisher, never()).publish(eq("payment-events"), any(), any());
    }
}
