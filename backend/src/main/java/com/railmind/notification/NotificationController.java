package com.railmind.notification;

import com.railmind.auth.CurrentUser;
import com.railmind.notification.dto.NotificationDtos.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public NotificationSummaryDto list(Authentication auth) {
        return notificationService.getForUser(currentUser.resolveId(auth));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id, Authentication auth) {
        return notificationService.markRead(id, currentUser.resolveId(auth));
    }

    @PostMapping("/read-all")
    public java.util.Map<String, Integer> markAllRead(Authentication auth) {
        int count = notificationService.markAllRead(currentUser.resolveId(auth));
        return java.util.Map.of("markedRead", count);
    }
}
