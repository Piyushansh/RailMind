package com.railmind.ai.agents;

import com.railmind.ai.agents.AgentDtos.AgentToolRequest;
import com.railmind.ai.agents.AgentDtos.AgentToolResponse;
import com.railmind.ai.tools.ToolExecutor;

/**
 * Shared logic for every concrete agent: refuse (fail-closed) any tool name
 * outside this agent's own permission set BEFORE ever touching
 * ToolExecutor, otherwise delegate to it. This is the actual enforcement
 * point for "an agent may only use its own tools" / "AI cannot execute
 * admin tools as a normal user" - a tool name that isn't in
 * allowedTools() never reaches ToolExecutor.execute() at all, regardless of
 * what the LLM asked for.
 *
 * Ownership scoping (a user can only see their own bookings/PNRs/etc.) and
 * "never invent data" (every read goes through a real service call) are
 * NOT re-implemented here - those guarantees already live in ToolExecutor
 * and the domain services it calls (BookingService, TrainSearchService, ...),
 * and re-implementing them at this layer would just be a second,
 * divergence-prone copy of the same rule. This class's job is narrowly
 * "does this agent own this tool", nothing more.
 */
public abstract class AbstractToolAgent implements Agent {

    protected final ToolExecutor toolExecutor;

    protected AbstractToolAgent(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public AgentToolResponse execute(AgentToolRequest request) {
        if (!allowedTools().contains(request.toolName())) {
            return new AgentToolResponse(name(), request.toolName(), errorJson(
                    name() + " is not authorized to execute tool \"" + request.toolName() + "\"."), false, false);
        }
        String resultJson = toolExecutor.execute(request.toolName(), request.input(), request.authenticatedUserId());
        boolean success = !resultJson.contains("\"error\"");
        return new AgentToolResponse(name(), request.toolName(), resultJson, true, success);
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
    }
}
