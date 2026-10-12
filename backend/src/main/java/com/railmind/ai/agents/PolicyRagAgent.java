package com.railmind.ai.agents;

import com.railmind.ai.tools.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Responsibility: answering railway-policy questions (cancellation, refunds,
 * booking rules, waiting list, quota, passenger rules, baggage, general
 * travel rules) grounded ONLY in retrieved policy document excerpts - never
 * from the model's own training knowledge. The single tool this agent owns,
 * policySearch, returns sufficientInformation=false when nothing clears the
 * similarity threshold; the system prompt (see AiOrchestratorService)
 * instructs the model to say so plainly rather than guess, and that
 * instruction is reinforced by this agent owning no other tool it could
 * use to fabricate an answer instead.
 */
@Component
public class PolicyRagAgent extends AbstractToolAgent {

    private static final Set<String> ALLOWED_TOOLS = Set.of("policySearch");

    public PolicyRagAgent(ToolExecutor toolExecutor) {
        super(toolExecutor);
    }

    @Override
    public String name() {
        return "PolicyRagAgent";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
