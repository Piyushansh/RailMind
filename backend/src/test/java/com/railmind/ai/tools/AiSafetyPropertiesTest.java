package com.railmind.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.auth.User;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.cancellation.RefundPolicyService;
import com.railmind.notification.NotificationService;
import com.railmind.profile.TravelPreferenceRepository;
import com.railmind.rag.PolicyRetrievalService;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.JourneyRankingService;
import com.railmind.train.TrainSearchService;
import com.railmind.train.dto.TrainDtos.AvailabilityDto;
import com.railmind.train.dto.TrainDtos.FareQuoteDto;
import com.railmind.disruption.TripAlertRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Direct proof of the safety invariants Phase 6 lists: booking/cancellation
 * "drafts" never call anything that persists a real booking, cancellation,
 * or refund; fare/availability figures the AI shows the user come verbatim
 * from the real services (never computed or rounded independently, which
 * would be a subtle form of hallucination); and RAG correctly reports
 * "insufficient information" instead of ever falling through to a
 * model-invented answer.
 */
class AiSafetyPropertiesTest {

    private ToolExecutor newExecutor(TrainSearchService trainSearchService, BookingService bookingService,
                                      PolicyRetrievalService policyRetrievalService) {
        return new ToolExecutor(trainSearchService, bookingService,
                mock(BookingRepositories.PnrRepo.class), mock(RefundPolicyService.class),
                mock(StationRepository.class), mock(JourneyRankingService.class),
                mock(NotificationService.class), mock(TripAlertRepository.class),
                mock(TravelPreferenceRepository.class), policyRetrievalService);
    }

    @Test
    void createBookingDraft_neverCallsAnyBookingServiceWriteMethod() throws Exception {
        TrainSearchService trainSearchService = mock(TrainSearchService.class);
        BookingService bookingService = mock(BookingService.class);
        StationRepository stationRepository = mock(StationRepository.class);

        Station source = new Station();
        source.setId(1L);
        source.setCode("NDLS");
        Station destination = new Station();
        destination.setId(2L);
        destination.setCode("BCT");
        when(stationRepository.findByCode("NDLS")).thenReturn(Optional.of(source));
        when(stationRepository.findByCode("BCT")).thenReturn(Optional.of(destination));
        when(trainSearchService.getRouteSpan(10L, 1L, 2L)).thenReturn(new TrainSearchService.RouteSpan(500, 480));
        when(trainSearchService.calculateFare(10L, "SL", 500))
                .thenReturn(new FareQuoteDto(10L, "SL", 500, BigDecimal.valueOf(150), BigDecimal.valueOf(0.55), BigDecimal.valueOf(425)));
        when(trainSearchService.getAvailability(100L)).thenReturn(List.of(new AvailabilityDto(100L, "SL", 72, 40)));

        ToolExecutor executor = new ToolExecutor(trainSearchService, bookingService,
                mock(BookingRepositories.PnrRepo.class), mock(RefundPolicyService.class), stationRepository,
                mock(JourneyRankingService.class), mock(NotificationService.class), mock(TripAlertRepository.class),
                mock(TravelPreferenceRepository.class), mock(PolicyRetrievalService.class));

        ObjectMapper mapper = new ObjectMapper();
        String input = "{\"trainId\":10,\"scheduleId\":100,\"travelClass\":\"SL\",\"passengerCount\":2,"
                + "\"sourceStationCode\":\"NDLS\",\"destinationStationCode\":\"BCT\"}";
        String result = executor.execute("createBookingDraft", mapper.readTree(input), 1L);

        assertThat(result).contains("\"draft\":true");
        assertThat(result).contains("425"); // per-passenger fare came straight from the mocked service
        // The whole point of a draft: NOTHING on BookingService that could persist a real
        // booking is ever called for this tool.
        verify(bookingService, never()).createBooking(any(), any());
        verifyNoMoreInteractions(bookingService);
    }

    @Test
    void createCancellationDraft_neverCallsAnyCancellationOrRefundWriteMethod() throws Exception {
        BookingService bookingService = mock(BookingService.class);
        RefundPolicyService refundPolicyService = mock(RefundPolicyService.class);

        User owner = new User();
        owner.setId(1L);
        Booking booking = new Booking();
        booking.setUser(owner);
        booking.setTotalFare(BigDecimal.valueOf(1000));
        var schedule = new com.railmind.train.ScheduleEntities.TrainSchedule();
        schedule.setDepartureDate(LocalDate.now().plusDays(3));
        schedule.setDepartureTime(LocalTime.of(10, 0));
        booking.setSchedule(schedule);

        when(bookingService.getRawEntity(5L, 1L)).thenReturn(booking);
        when(refundPolicyService.calculate(eq(BigDecimal.valueOf(1000)), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new RefundPolicyService.RefundQuote(BigDecimal.valueOf(1000), BigDecimal.valueOf(25),
                        BigDecimal.valueOf(750), "25% deduction"));

        ToolExecutor executor = new ToolExecutor(mock(TrainSearchService.class), bookingService,
                mock(BookingRepositories.PnrRepo.class), refundPolicyService, mock(StationRepository.class),
                mock(JourneyRankingService.class), mock(NotificationService.class), mock(TripAlertRepository.class),
                mock(TravelPreferenceRepository.class), mock(PolicyRetrievalService.class));

        ObjectMapper mapper = new ObjectMapper();
        String result = executor.execute("createCancellationDraft", mapper.readTree("{\"bookingId\":5}"), 1L);

        assertThat(result).contains("\"draft\":true");
        assertThat(result).contains("750"); // refund figure came straight from the policy engine, not invented
        // No cancellation or refund was ever actually processed - this tool only quotes.
        verify(bookingService, never()).cancelConfirmedBooking(any(), any());
        verifyNoMoreInteractions(bookingService);
    }

    @Test
    void policySearch_reportsInsufficientInformation_whenNothingClearsTheSimilarityThreshold() throws Exception {
        PolicyRetrievalService policyRetrievalService = mock(PolicyRetrievalService.class);
        when(policyRetrievalService.retrieve("some obscure question")).thenReturn(List.of());

        ToolExecutor executor = newExecutor(mock(TrainSearchService.class), mock(BookingService.class), policyRetrievalService);

        ObjectMapper mapper = new ObjectMapper();
        String result = executor.execute("policySearch", mapper.readTree("{\"query\":\"some obscure question\"}"), 1L);

        assertThat(result).contains("\"sufficientInformation\":false");
        // Must not contain a fabricated "results" array when there's nothing grounded to show.
        assertThat(result).doesNotContain("\"results\"");
    }
}
