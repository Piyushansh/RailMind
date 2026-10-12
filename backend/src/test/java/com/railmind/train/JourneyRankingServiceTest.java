package com.railmind.train;

import com.railmind.train.dto.JourneyPlanDtos.JourneyPlanResult;
import com.railmind.train.dto.JourneyPlanDtos.RankedCategory;
import com.railmind.train.dto.TrainDtos.SeatClassAvailability;
import com.railmind.train.dto.TrainDtos.TrainSearchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the ranking categories actually reorder/filter results correctly -
 * this is the deterministic logic the AI is required to defer to rather than
 * ranking things itself, so it earns a real test rather than a smoke test.
 */
class JourneyRankingServiceTest {

    private TrainSearchResult result(long scheduleId, LocalTime departure, int durationMinutes, int delayMinutes, String travelClass, double fare) {
        return new TrainSearchResult(scheduleId, 1L, "RM10" + scheduleId, "Test Express", "EXPRESS",
                "NDLS", "BCT", LocalDate.now(), departure, durationMinutes, "SCHEDULED", delayMinutes,
                List.of(new SeatClassAvailability(travelClass, 50, 10, BigDecimal.valueOf(fare))));
    }

    @Test
    void cheapestCategory_picksLowestFareFirst() {
        TrainSearchService searchService = mock(TrainSearchService.class);
        JourneyRankingService ranking = new JourneyRankingService(searchService);

        TrainSearchResult expensive = result(1, LocalTime.of(10, 0), 600, 0, "AC3", 1200);
        TrainSearchResult cheap = result(2, LocalTime.of(11, 0), 700, 0, "AC3", 450);

        when(searchService.search("NDLS", "BCT", LocalDate.now(), null, "recommended"))
                .thenReturn(List.of(expensive, cheap));

        JourneyPlanResult plan = ranking.plan("NDLS", "BCT", LocalDate.now(), null, null);

        RankedCategory cheapest = plan.categories().stream()
                .filter(c -> c.category().equals("Cheapest")).findFirst().orElseThrow();
        assertThat(cheapest.options().get(0).scheduleId()).isEqualTo(2L);
    }

    @Test
    void bestOvernight_onlyIncludesEveningOrLateNightDepartures() {
        TrainSearchService searchService = mock(TrainSearchService.class);
        JourneyRankingService ranking = new JourneyRankingService(searchService);

        TrainSearchResult morning = result(1, LocalTime.of(9, 0), 600, 0, "SL", 300);
        TrainSearchResult evening = result(2, LocalTime.of(20, 0), 600, 0, "SL", 300);

        when(searchService.search("NDLS", "BCT", LocalDate.now(), null, "recommended"))
                .thenReturn(List.of(morning, evening));

        JourneyPlanResult plan = ranking.plan("NDLS", "BCT", LocalDate.now(), null, null);

        RankedCategory overnight = plan.categories().stream()
                .filter(c -> c.category().equals("Best Overnight")).findFirst().orElseThrow();
        assertThat(overnight.options()).extracting(TrainSearchResult::scheduleId).containsExactly(2L);
    }

    @Test
    void maxFare_excludesOptionsAboveBudget() {
        TrainSearchService searchService = mock(TrainSearchService.class);
        JourneyRankingService ranking = new JourneyRankingService(searchService);

        TrainSearchResult withinBudget = result(1, LocalTime.of(10, 0), 600, 0, "SL", 300);
        TrainSearchResult overBudget = result(2, LocalTime.of(11, 0), 600, 0, "AC1", 3000);

        when(searchService.search("NDLS", "BCT", LocalDate.now(), null, "recommended"))
                .thenReturn(List.of(withinBudget, overBudget));

        JourneyPlanResult plan = ranking.plan("NDLS", "BCT", LocalDate.now(), null, BigDecimal.valueOf(1000));

        RankedCategory cheapest = plan.categories().stream()
                .filter(c -> c.category().equals("Cheapest")).findFirst().orElseThrow();
        assertThat(cheapest.options()).extracting(TrainSearchResult::scheduleId).containsExactly(1L);
    }
}
