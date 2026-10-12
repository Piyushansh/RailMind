package com.railmind.ai.agents;

import com.railmind.ai.tools.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Responsibility: surfacing disruption-related notifications (delay,
 * cancellation, platform-change alerts the system already generated) to the
 * user in conversation. Live disruption DETECTION itself happens entirely
 * server-side (Kafka train-events -> DisruptionService -> TripAlert +
 * Notification, see the disruption package) - this agent never detects or
 * creates a disruption itself, it only reads back what the system already
 * decided, the same way a human support agent would relay an alert rather
 * than generate one. For the live per-trip status/alert view (delay,
 * platform, suggested alternative), see TripGuardianAgent, which is the
 * user-initiated "what's happening with MY trip right now" counterpart to
 * this agent's "what has the system already told me" role.
 */
@Component
public class DisruptionAgent extends AbstractToolAgent {

    private static final Set<String> ALLOWED_TOOLS = Set.of("getNotifications");

    public DisruptionAgent(ToolExecutor toolExecutor) {
        super(toolExecutor);
    }

    @Override
    public String name() {
        return "DisruptionAgent";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
