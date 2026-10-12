package com.railmind.notification;

import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Notifications follow the same ownership rule as bookings/PNRs elsewhere in
 * this codebase: findByIdAndUserId (not findById) is what makes cross-user
 * access impossible by construction. This test locks in that markRead can
 * never touch another user's notification, mirroring
 * BookingIdempotencyOwnershipTest and ToolExecutorAuthorizationTest.
 */
class NotificationServiceOwnershipTest {

    @Test
    void markRead_forNotificationNotOwnedByCaller_throwsNotFound_neverLeaksIt() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService.EmailSender emailSender = mock(NotificationService.EmailSender.class);
        NotificationService service = new NotificationService(repository, emailSender,
                mock(com.railmind.events.DomainEventPublisher.class), "notification-events");

        // findByIdAndUserId(1L, 42L) correctly returns empty because notification 1
        // belongs to some other user - the repository query itself enforces ownership.
        when(repository.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(1L, 42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
