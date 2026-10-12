package com.railmind.ai.agents;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Every agent speaks in these two shapes. A single shared pair (rather than
 * 12 bespoke per-tool DTOs) is a deliberate choice: the actual heterogeneous
 * per-tool input/output shapes already live in ToolExecutor and the real
 * domain services it calls (TrainSearchService, BookingService, etc.) -
 * duplicating those shapes here would just be a second, unenforced copy of
 * the same contract. What's genuinely new and worth a dedicated type at the
 * agent layer is the REQUEST/RESPONSE ENVELOPE: which tool, whose request,
 * and whether it was allowed and succeeded.
 */
public class AgentDtos {

    public record AgentToolRequest(String toolName, JsonNode input, Long authenticatedUserId) {}

    public record AgentToolResponse(String agentName, String toolName, String resultJson, boolean authorized, boolean success) {}
}
