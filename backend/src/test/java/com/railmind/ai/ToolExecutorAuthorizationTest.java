package com.railmind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.ai.tools.ToolExecutor;
import com.railmind.auth.User;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingEntities.Pnr;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.cancellation.RefundPolicyService;
import com.railmind.station.StationRepository;
import com.railmind.train.TrainSearchService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the "AI tool authorization" item from the test checklist: a tool
 * call for a PNR that belongs to a DIFFERENT user must come back as a safe
 * error the model can relay, never the other user's booking data. This is
 * the concrete enforcement of "LLM never bypasses authorization" from the
 * system prompt - proven here independent of any LLM, against ToolExecutor
 * directly.
 */
class ToolExecutorAuthorizationTest {

    @Test
    void getPnrTool_forAnotherUsersBooking_returnsErrorNotData() throws Exception {
        TrainSearchService trainSearchService = mock(TrainSearchService.class);
        BookingService bookingService = mock(BookingService.class);
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        RefundPolicyService refundPolicyService = mock(RefundPolicyService.class);
        StationRepository stationRepository = mock(StationRepository.class);

        com.railmind.train.JourneyRankingService journeyRankingService = mock(com.railmind.train.JourneyRankingService.class);
        com.railmind.notification.NotificationService notificationService = mock(com.railmind.notification.NotificationService.class);
        com.railmind.disruption.TripAlertRepository tripAlertRepository = mock(com.railmind.disruption.TripAlertRepository.class);
        com.railmind.profile.TravelPreferenceRepository travelPreferenceRepository = mock(com.railmind.profile.TravelPreferenceRepository.class);
        com.railmind.rag.PolicyRetrievalService policyRetrievalService = mock(com.railmind.rag.PolicyRetrievalService.class);
        ToolExecutor executor = new ToolExecutor(trainSearchService, bookingService, pnrRepo, refundPolicyService,
                stationRepository, journeyRankingService, notificationService, tripAlertRepository,
                travelPreferenceRepository, policyRetrievalService);

        User otherUser = new User();
        otherUser.setId(777L);

        Booking someoneElsesBooking = new Booking();
        someoneElsesBooking.setUser(otherUser);

        Pnr pnr = new Pnr();
        pnr.setBooking(someoneElsesBooking);
        pnr.setPnrNumber("1234567890");

        when(pnrRepo.findByPnrNumber("1234567890")).thenReturn(Optional.of(pnr));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode input = mapper.readTree("{\"pnrNumber\":\"1234567890\"}");

        // authenticatedUserId (42) does NOT own this PNR (owner is 777)
        String result = executor.execute("getPNR", input, 42L);

        assertThat(result).contains("error");
        assertThat(result).doesNotContain("777");
    }
}
