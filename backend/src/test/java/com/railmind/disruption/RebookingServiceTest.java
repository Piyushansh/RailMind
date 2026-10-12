package com.railmind.disruption;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.cancellation.CancellationService;
import com.railmind.common.exceptions.AppExceptions.SeatUnavailableException;
import com.railmind.station.Station;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The key safety property of the rebooking flow: seats on the alternative
 * schedule are secured BEFORE the original booking is touched. If securing
 * them fails (no availability), the original booking must be left completely
 * alone - never cancelled with nothing to replace it.
 */
class RebookingServiceTest {

    @Test
    void whenAlternativeHasNoSeats_originalBookingIsNeverCancelled() {
        BookingService bookingService = mock(BookingService.class);
        CancellationService cancellationService = mock(CancellationService.class);
        TripAlertRepository tripAlertRepository = mock(TripAlertRepository.class);
        AuditService auditService = mock(AuditService.class);

        RebookingService rebookingService = new RebookingService(bookingService, cancellationService, tripAlertRepository, auditService);

        Station source = new Station();
        source.setId(1L);
        Station destination = new Station();
        destination.setId(2L);

        User user = new User();
        user.setId(42L);

        Booking oldBooking = new Booking();
        oldBooking.setId(500L);
        oldBooking.setUser(user);
        oldBooking.setStatus("CONFIRMED");
        oldBooking.setSourceStation(source);
        oldBooking.setDestinationStation(destination);
        oldBooking.setTravelClass("SL");
        oldBooking.setPassengers(List.of());

        TrainSchedule alternativeSchedule = new TrainSchedule();
        alternativeSchedule.setId(20L);

        TripAlert alert = new TripAlert();
        alert.setId(999L);
        alert.setBooking(oldBooking);
        alert.setSuggestedAlternativeSchedule(alternativeSchedule);

        when(tripAlertRepository.findByIdAndBooking_Id(999L, 500L)).thenReturn(Optional.of(alert));
        when(bookingService.getRawEntity(500L, 42L)).thenReturn(oldBooking);
        when(bookingService.createConfirmedRebooking(eq(42L), eq(20L), eq(1L), eq(2L), eq("SL"), anyList(), eq(500L)))
                .thenThrow(new SeatUnavailableException("No available seats in class SL for this schedule."));

        assertThatThrownBy(() -> rebookingService.approveRebooking(42L, 500L, 999L))
                .isInstanceOf(SeatUnavailableException.class);

        // The old booking must never be cancelled/refunded if the new one couldn't be secured.
        verifyNoInteractions(cancellationService);
        verify(tripAlertRepository, never()).save(argThat(a -> a.isResolved()));
    }

    @Test
    void whenAlternativeSucceeds_oldBookingIsCancelledWithFullDisruptionRefund() {
        BookingService bookingService = mock(BookingService.class);
        CancellationService cancellationService = mock(CancellationService.class);
        TripAlertRepository tripAlertRepository = mock(TripAlertRepository.class);
        AuditService auditService = mock(AuditService.class);

        RebookingService rebookingService = new RebookingService(bookingService, cancellationService, tripAlertRepository, auditService);

        Station source = new Station();
        source.setId(1L);
        Station destination = new Station();
        destination.setId(2L);
        User user = new User();
        user.setId(42L);

        Booking oldBooking = new Booking();
        oldBooking.setId(500L);
        oldBooking.setUser(user);
        oldBooking.setStatus("CONFIRMED");
        oldBooking.setSourceStation(source);
        oldBooking.setDestinationStation(destination);
        oldBooking.setTravelClass("SL");
        oldBooking.setPassengers(List.of());

        TrainSchedule alternativeSchedule = new TrainSchedule();
        alternativeSchedule.setId(20L);

        TripAlert alert = new TripAlert();
        alert.setId(999L);
        alert.setBooking(oldBooking);
        alert.setSuggestedAlternativeSchedule(alternativeSchedule);

        BookingDto newBookingDto = new BookingDto(600L, 20L, "CONFIRMED", "RM999", "Alt Express",
                "NDLS", "BCT", "SL", java.math.BigDecimal.valueOf(300), List.of(), "PNR0000600", false, java.time.Instant.now());

        when(tripAlertRepository.findByIdAndBooking_Id(999L, 500L)).thenReturn(Optional.of(alert));
        when(bookingService.getRawEntity(500L, 42L)).thenReturn(oldBooking);
        when(bookingService.createConfirmedRebooking(eq(42L), eq(20L), eq(1L), eq(2L), eq("SL"), anyList(), eq(500L)))
                .thenReturn(newBookingDto);

        BookingDto result = rebookingService.approveRebooking(42L, 500L, 999L);

        verify(cancellationService).cancelDueToDisruption(eq(42L), eq(500L), anyString());
        verify(tripAlertRepository).save(argThat(a -> a.getId() != null && a.isResolved()));
        assert result.bookingId().equals(600L);
    }
}
