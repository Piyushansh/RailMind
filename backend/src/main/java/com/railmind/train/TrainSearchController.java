package com.railmind.train;

import com.railmind.train.dto.TrainDtos.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trains")
public class TrainSearchController {

    private final TrainSearchService trainSearchService;
    private final com.railmind.train.JourneyRankingService journeyRankingService;

    public TrainSearchController(TrainSearchService trainSearchService, com.railmind.train.JourneyRankingService journeyRankingService) {
        this.trainSearchService = trainSearchService;
        this.journeyRankingService = journeyRankingService;
    }

    @GetMapping("/search")
    public List<TrainSearchResult> search(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String travelClass,
            @RequestParam(required = false, defaultValue = "recommended") String sortBy
    ) {
        return trainSearchService.search(source, destination, date, travelClass, sortBy);
    }

    @GetMapping("/{trainId}/details")
    public TrainDetailsDto details(@PathVariable Long trainId) {
        return trainSearchService.getTrainDetails(trainId);
    }

    @GetMapping("/schedules/{scheduleId}/availability")
    public List<AvailabilityDto> availability(@PathVariable Long scheduleId) {
        return trainSearchService.getAvailability(scheduleId);
    }

    @GetMapping("/{trainId}/fare")
    public FareQuoteDto fare(@PathVariable Long trainId, @RequestParam String travelClass, @RequestParam int distanceKm) {
        return trainSearchService.calculateFare(trainId, travelClass, distanceKm);
    }

    /** Deterministic ranked journey plan (Best Overall / Cheapest / Fastest / Best Overnight / Most Reliable). */
    @GetMapping("/plan")
    public com.railmind.train.dto.JourneyPlanDtos.JourneyPlanResult plan(
            @RequestParam String source,
            @RequestParam String destination,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String travelClass,
            @RequestParam(required = false) java.math.BigDecimal maxFare
    ) {
        return journeyRankingService.plan(source, destination, date, travelClass, maxFare);
    }
}
