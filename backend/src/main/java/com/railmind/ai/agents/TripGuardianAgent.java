package com.railmind.ai.agents;

import com.railmind.ai.tools.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Responsibility: explaining a specific booking's live Trip Guardian state -
 * current schedule status/delay/platform, whether Trip Guardian is enabled,
 * and any open alert with a suggested alternative. This agent can describe
 * an alert and its suggested alternative, but it has NO tool that approves
 * or rejects a rebooking, and none that enables/disables Trip Guardian -
 * those stay real, explicit user actions against
 * POST /api/v1/bookings/{id}/trip-alerts/{alertId}/approve-rebooking (or
 * .../reject-rebooking) and POST /api/v1/bookings/{id}/trip-guardian, taken
 * by the user directly in the UI, never something this agent can trigger.
 * getRawEntity/getBooking-style ownership scoping for "whose booking is
 * this" is enforced inside ToolExecutor.getTripStatus, not here.
 */
@Component
public class TripGuardianAgent extends AbstractToolAgent {

    private static final Set<String> ALLOWED_TOOLS = Set.of("getTripStatus");

    public TripGuardianAgent(ToolExecutor toolExecutor) {
        super(toolExecutor);
    }

    @Override
    public String name() {
        return "TripGuardianAgent";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
