package com.railmind.notification;

import com.railmind.booking.BookingEntities.Booking;
import com.railmind.common.exceptions.AppExceptions.ForbiddenException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.events.DomainEventPublisher;
import com.railmind.notification.dto.NotificationDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * In-app notification history plus a mock "would-have-emailed" log line.
 * There is no real email/SMS provider wired in - EmailSender below is the
 * abstraction point for one, kept intentionally small so it's obvious what
 * to implement to add real delivery later (this mirrors the PaymentProvider
 * pattern already used for payments: interface now, real implementation
 * later, nothing else in the codebase needs to change when that happens).
 *
 * Every notification is also published to notification-events (fire-and-
 * forget - see DomainEventPublisher) alongside the existing synchronous
 * write - this is the real, non-decorative home for that topic, since a
 * "notification" IS the event, not a side effect of one.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final EmailSender emailSender;
    private final DomainEventPublisher eventPublisher;
    private final String notificationEventsTopic;

    public NotificationService(NotificationRepository repository, EmailSender emailSender,
                                DomainEventPublisher eventPublisher,
                                @Value("${railmind.kafka.notification-events-topic}") String notificationEventsTopic) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.notificationEventsTopic = notificationEventsTopic;
    }

    @Transactional
    public void notify(com.railmind.auth.User user, String type, String title, String message, Booking relatedBooking) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedBooking(relatedBooking);
        repository.save(notification);

        emailSender.send(user.getEmail(), title, message);

        eventPublisher.publish(notificationEventsTopic, type, Map.of(
                "userId", user.getId(),
                "type", type,
                "title", title,
                "relatedBookingId", relatedBooking != null ? relatedBooking.getId() : 0L));
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto getForUser(Long userId) {
        List<NotificationDto> notifications = repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
        long unread = repository.countByUserIdAndReadFalse(userId);
        return new NotificationSummaryDto(unread, notifications);
    }

    @Transactional
    public NotificationDto markRead(Long notificationId, Long userId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        return toDto(repository.save(notification));
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = repository.findByUserIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        repository.saveAll(unread);
        return unread.size();
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getRelatedBooking() != null ? n.getRelatedBooking().getId() : null,
                n.isRead(), n.getCreatedAt()
        );
    }

    /** Minimal abstraction over "send an email" - logs only for now, matching the mock-provider pattern used for payments. */
    public interface EmailSender {
        void send(String toEmail, String subject, String body);
    }

    @org.springframework.stereotype.Component
    public static class MockEmailSender implements EmailSender {
        private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

        @Override
        public void send(String toEmail, String subject, String body) {
            log.info("[mock email] to={} subject=\"{}\" body=\"{}\"", toEmail, subject, body);
        }
    }
}
