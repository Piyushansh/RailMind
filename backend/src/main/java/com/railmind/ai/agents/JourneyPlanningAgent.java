package com.railmind.ai.agents;

import com.railmind.ai.tools.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Responsibility: helping a user find and compare trains - search,
 * availability, fares, ranked journey plans, and reading back their saved
 * travel preferences to personalize recommendations. Entirely read-only:
 * none of these tools write anything or require ownership scoping beyond
 * "which user's saved preferences" (enforced inside ToolExecutor).
 */
@Component
public class JourneyPlanningAgent extends AbstractToolAgent {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "searchTrains", "findAlternativeTrains", "getTrainDetails",
            "checkAvailability", "calculateFare", "planJourney", "getUserPreferences");

    public JourneyPlanningAgent(ToolExecutor toolExecutor) {
        super(toolExecutor);
    }

    @Override
    public String name() {
        return "JourneyPlanningAgent";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
