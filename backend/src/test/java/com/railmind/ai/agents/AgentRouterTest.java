package com.railmind.ai.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.ai.agents.AgentDtos.AgentToolRequest;
import com.railmind.ai.tools.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the "Agent Selection" + fail-closed requirements from Phase 6:
 * a registered tool routes to its real owning agent, and a tool name no
 * agent claims (including a hypothetical admin-only or entirely made-up
 * tool - simulating a prompt-injection attempt to invoke something outside
 * the real tool surface) is refused before ToolExecutor is ever called.
 */
class AgentRouterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AgentRouter newRealRouter(ToolExecutor toolExecutor) {
        return new AgentRouter(
                new JourneyPlanningAgent(toolExecutor),
                new BookingAgent(toolExecutor),
                new PolicyRagAgent(toolExecutor),
                new DisruptionAgent(toolExecutor),
                new TripGuardianAgent(toolExecutor));
    }

    @Test
    void routesARealTool_toItsOwningAgent() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        when(toolExecutor.execute(eq("searchTrains"), any(), eq(1L))).thenReturn("[]");
        AgentRouter router = newRealRouter(toolExecutor);

        var response = router.route(new AgentToolRequest("searchTrains", mapper.readTree("{}"), 1L));

        assertThat(response.agentName()).isEqualTo("JourneyPlanningAgent");
        assertThat(response.authorized()).isTrue();
        verify(toolExecutor).execute("searchTrains", mapper.readTree("{}"), 1L);
    }

    @Test
    void unregisteredToolName_isRefused_withoutEverCallingToolExecutor() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentRouter router = newRealRouter(toolExecutor);

        // Simulates a prompt-injection-style attempt to invoke something outside the real
        // tool surface entirely, including anything that sounds admin-flavored.
        var response = router.route(new AgentToolRequest("adminDeleteAllBookings", mapper.readTree("{}"), 1L));

        assertThat(response.authorized()).isFalse();
        assertThat(response.resultJson()).contains("error");
        verifyNoInteractions(toolExecutor);
    }

    @Test
    void everyRealToolName_hasExactlyOneOwningAgent() {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentRouter router = newRealRouter(toolExecutor);

        var allToolNames = router.allAgents().stream().flatMap(a -> a.allowedTools().stream()).toList();
        var distinctToolNames = allToolNames.stream().distinct().toList();

        assertThat(allToolNames).hasSameSizeAs(distinctToolNames);
    }

    @Test
    void constructorRejectsTwoAgentsClaimingTheSameTool() {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        Agent first = new AbstractToolAgent(toolExecutor) {
            @Override public String name() { return "FirstAgent"; }
            @Override public Set<String> allowedTools() { return Set.of("searchTrains"); }
        };
        Agent rogue = new AbstractToolAgent(toolExecutor) {
            @Override public String name() { return "RogueAgent"; }
            @Override public Set<String> allowedTools() { return Set.of("searchTrains"); }
        };

        assertThatThrownBy(() -> new AgentRouter(List.of(first, rogue)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("searchTrains");
    }
}
