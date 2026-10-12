package com.railmind.train.dto;

import com.railmind.train.dto.TrainDtos.TrainSearchResult;

import java.math.BigDecimal;
import java.util.List;

public class JourneyPlanDtos {

    /** One named category (e.g. "Cheapest") with its picks and why they were picked. */
    public record RankedCategory(String category, String reason, List<TrainSearchResult> options) {}

    public record JourneyPlanResult(
            String sourceStationCode,
            String destinationStationCode,
            java.time.LocalDate date,
            String travelClassFilter,
            java.math.BigDecimal maxFare,
            List<RankedCategory> categories
    ) {}
}
