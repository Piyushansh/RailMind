package com.railmind.ai.agents;

import com.railmind.ai.agents.AgentDtos.AgentToolRequest;
import com.railmind.ai.agents.AgentDtos.AgentToolResponse;

import java.util.Set;

/**
 * A specialist agent: a named, bounded slice of the tool surface with its
 * own permission set. Five concrete implementations exist -
 * JourneyPlanningAgent, BookingAgent, PolicyRagAgent, DisruptionAgent,
 * TripGuardianAgent - see AgentRouter for how a tool call is matched to its
 * owning agent, and AbstractToolAgent for the shared validate/execute/audit
 * logic every agent gets for free.
 */
public interface Agent {
    String name();
    Set<String> allowedTools();
    AgentToolResponse execute(AgentToolRequest request);
}
