package com.railmind.ai.agents;

import com.railmind.ai.agents.AgentDtos.AgentToolRequest;
import com.railmind.ai.agents.AgentDtos.AgentToolResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Agent Selection" step in the workflow this phase specifies:
 *   User Request -> Intent Detection -> Agent Selection -> Tool Validation -> ...
 * "Intent detection" in a tool-calling architecture IS the model choosing
 * which tool to call (see AiOrchestratorService's single conversation loop,
 * unchanged this phase) - there is no separate NLU classifier before it,
 * because the model's tool choice already encodes that decision. What this
 * router adds on top is agent selection + a fail-closed default: a tool
 * name that isn't registered to any agent is refused outright, never routed
 * to a "default" agent that might execute it anyway. This is what makes
 * "AI cannot execute admin tools as a normal user" true by construction -
 * an admin tool, or any future tool nobody explicitly assigned to an agent,
 * simply has no path to execution here.
 */
@Component
public class AgentRouter {

    private final Map<String, Agent> toolToAgent = new HashMap<>();
    private final List<Agent> agents;

    public AgentRouter(JourneyPlanningAgent journeyPlanningAgent, BookingAgent bookingAgent,
                        PolicyRagAgent policyRagAgent, DisruptionAgent disruptionAgent,
                        TripGuardianAgent tripGuardianAgent) {
        this(List.of(journeyPlanningAgent, bookingAgent, policyRagAgent, disruptionAgent, tripGuardianAgent));
    }

    /** Package-visible so the duplicate-registration safety check is directly testable with an
     * arbitrary set of agents, without needing five real constructor-typed instances. */
    AgentRouter(List<Agent> agents) {
        this.agents = agents;
        for (Agent agent : agents) {
            for (String tool : agent.allowedTools()) {
                Agent existing = toolToAgent.put(tool, agent);
                if (existing != null) {
                    throw new IllegalStateException("Tool \"" + tool + "\" is claimed by both "
                            + existing.name() + " and " + agent.name() + " - each tool must have exactly one owning agent.");
                }
            }
        }
    }

    public List<Agent> allAgents() {
        return agents;
    }

    public AgentToolResponse route(AgentToolRequest request) {
        Agent agent = toolToAgent.get(request.toolName());
        if (agent == null) {
            return new AgentToolResponse("none", request.toolName(),
                    "{\"error\":\"No agent is registered for tool \\\"" + request.toolName() + "\\\".\"}", false, false);
        }
        return agent.execute(request);
    }
}
