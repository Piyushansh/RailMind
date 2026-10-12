package com.railmind.train;

import com.railmind.train.dto.JourneyPlanDtos.*;
import com.railmind.train.dto.TrainDtos.SeatClassAvailability;
import com.railmind.train.dto.TrainDtos.TrainSearchResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic ranking on top of TrainSearchService's real search results.
 * This is what the AI Journey Planning Agent calls (via the planJourney tool)
 * instead of ranking options itself in prose - the categories, order, and
 * "why" are all computed here from real fare/availability/delay data, and
 * the AI is only ever allowed to explain this output, never produce its own.
 */
@Service
public class JourneyRankingService {

    private final TrainSearchService trainSearchService;

    public JourneyRankingService(TrainSearchService trainSearchService) {
        this.trainSearchService = trainSearchService;
    }

    public JourneyPlanResult plan(String sourceCode, String destinationCode, LocalDate date,
                                   String travelClass, BigDecimal maxFare) {
        List<TrainSearchResult> all = trainSearchService.search(sourceCode, destinationCode, date, travelClass, "recommended");

        List<TrainSearchResult> withinBudget = maxFare == null
                ? all
                : all.stream().filter(r -> cheapestFare(r).compareTo(maxFare) <= 0).toList();

        List<RankedCategory> categories = List.of(
                bestOverall(withinBudget),
                cheapest(withinBudget),
                fastest(withinBudget),
                bestOvernight(withinBudget),
                mostReliable(withinBudget)
        );

        return new JourneyPlanResult(sourceCode, destinationCode, date, travelClass, maxFare, categories);
    }

    private RankedCategory bestOverall(List<TrainSearchResult> options) {
        // Balances duration and price - same ordering TrainSearchService uses for "recommended".
        List<TrainSearchResult> ranked = options.stream()
                .sorted(Comparator.comparingInt(TrainSearchResult::durationMinutes).thenComparing(this::cheapestFare))
                .limit(3)
                .toList();
        return new RankedCategory("Best Overall",
                "Balances shorter travel time with lower fare across available classes.", ranked);
    }

    private RankedCategory cheapest(List<TrainSearchResult> options) {
        List<TrainSearchResult> ranked = options.stream()
                .filter(r -> !r.classes().isEmpty())
                .sorted(Comparator.comparing(this::cheapestFare))
                .limit(3)
                .toList();
        return new RankedCategory("Cheapest",
                "Lowest fare found across classes for this route and date.", ranked);
    }

    private RankedCategory fastest(List<TrainSearchResult> options) {
        List<TrainSearchResult> ranked = options.stream()
                .sorted(Comparator.comparingInt(TrainSearchResult::durationMinutes))
                .limit(3)
                .toList();
        return new RankedCategory("Fastest",
                "Shortest scheduled journey duration.", ranked);
    }

    private RankedCategory bestOvernight(List<TrainSearchResult> options) {
        List<TrainSearchResult> overnight = options.stream()
                .filter(r -> isOvernightDeparture(r.departureTime()))
                .sorted(Comparator.comparingInt(TrainSearchResult::durationMinutes))
                .limit(3)
                .toList();
        String reason = overnight.isEmpty()
                ? "No trains depart in the typical overnight window (6 PM - midnight) for this route/date."
                : "Departs in the evening/night window, letting you sleep through most of the journey.";
        return new RankedCategory("Best Overnight", reason, overnight);
    }

    private RankedCategory mostReliable(List<TrainSearchResult> options) {
        List<TrainSearchResult> ranked = options.stream()
                .sorted(Comparator.comparingInt(TrainSearchResult::delayMinutes))
                .limit(3)
                .toList();
        return new RankedCategory("Most Reliable",
                "Lowest currently-reported delay for this schedule.", ranked);
    }

    private boolean isOvernightDeparture(LocalTime departureTime) {
        return !departureTime.isBefore(LocalTime.of(18, 0)) || departureTime.isBefore(LocalTime.of(2, 0));
    }

    private BigDecimal cheapestFare(TrainSearchResult result) {
        return result.classes().stream()
                .map(SeatClassAvailability::fare)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
    }
}
