package com.railmind.notification.dto;

import java.time.Instant;

public class NotificationDtos {

    public record NotificationDto(
            Long id,
            String type,
            String title,
            String message,
            Long relatedBookingId,
            boolean read,
            Instant createdAt
    ) {}

    public record NotificationSummaryDto(
            long unreadCount,
            java.util.List<NotificationDto> notifications
    ) {}
}
