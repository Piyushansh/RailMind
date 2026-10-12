package com.railmind.ai.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railmind.ai.agents.AgentDtos.AgentToolRequest;
import com.railmind.ai.tools.ToolExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The core "tool authorization" test Phase 6 asks for: each agent must
 * refuse a tool call outside its own declared permission set, and that
 * refusal must happen BEFORE ToolExecutor (the actual gateway to real
 * services/database) is ever invoked - not just that the result happens to
 * look like an error. Also covers "AI cannot execute admin tools as a
 * normal user": no agent's permission set contains anything admin-flavored,
 * and a made-up admin-sounding tool name is refused by every agent.
 */
class AgentAuthorizationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void journeyPlanningAgent_executesItsOwnTools_refusesOthers() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        when(toolExecutor.execute(eq("calculateFare"), any(), eq(1L))).thenReturn("{}");
        JourneyPlanningAgent agent = new JourneyPlanningAgent(toolExecutor);

        var allowed = agent.execute(new AgentToolRequest("calculateFare", mapper.readTree("{}"), 1L));
        assertThat(allowed.authorized()).isTrue();
        verify(toolExecutor).execute("calculateFare", mapper.readTree("{}"), 1L);

        var refused = agent.execute(new AgentToolRequest("createBookingDraft", mapper.readTree("{}"), 1L));
        assertThat(refused.authorized()).isFalse();
        verify(toolExecutor, never()).execute(eq("createBookingDraft"), any(), any());
    }

    @Test
    void bookingAgent_cannotExecuteJourneyPlanningOrPolicyTools() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        BookingAgent agent = new BookingAgent(toolExecutor);

        var refused = agent.execute(new AgentToolRequest("searchTrains", mapper.readTree("{}"), 1L));
        assertThat(refused.authorized()).isFalse();

        var refusedPolicy = agent.execute(new AgentToolRequest("policySearch", mapper.readTree("{}"), 1L));
        assertThat(refusedPolicy.authorized()).isFalse();

        verifyNoInteractions(toolExecutor);
    }

    @Test
    void policyRagAgent_onlyEverExecutesPolicySearch() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        when(toolExecutor.execute(eq("policySearch"), any(), eq(1L))).thenReturn("{\"sufficientInformation\":true}");
        PolicyRagAgent agent = new PolicyRagAgent(toolExecutor);

        assertThat(agent.allowedTools()).containsExactly("policySearch");

        var refused = agent.execute(new AgentToolRequest("createBookingDraft", mapper.readTree("{}"), 1L));
        assertThat(refused.authorized()).isFalse();
        verify(toolExecutor, never()).execute(eq("createBookingDraft"), any(), any());
    }

    @Test
    void noAgent_hasAnyAdminOrMutatingToolInItsPermissionSet() {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        var agents = java.util.List.of(
                new JourneyPlanningAgent(toolExecutor), new BookingAgent(toolExecutor),
                new PolicyRagAgent(toolExecutor), new DisruptionAgent(toolExecutor),
                new TripGuardianAgent(toolExecutor));

        for (Agent agent : agents) {
            for (String tool : agent.allowedTools()) {
                String lower = tool.toLowerCase();
                assertThat(lower).as("tool '%s' owned by %s looks admin-flavored", tool, agent.name())
                        .doesNotContain("admin");
                assertThat(lower).as("tool '%s' owned by %s looks like a direct mutation, not a draft/read", tool, agent.name())
                        .doesNotMatch(".*(confirmbooking|executecancellation|processrefund|approverebooking|rejectrebooking).*");
            }
        }
    }

    @Test
    void everyAgent_refusesAMadeUpAdminSoundingTool() throws Exception {
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        var agents = java.util.List.of(
                new JourneyPlanningAgent(toolExecutor), new BookingAgent(toolExecutor),
                new PolicyRagAgent(toolExecutor), new DisruptionAgent(toolExecutor),
                new TripGuardianAgent(toolExecutor));

        for (Agent agent : agents) {
            var response = agent.execute(new AgentToolRequest("adminBlockUser", mapper.readTree("{}"), 1L));
            assertThat(response.authorized()).as("%s must refuse adminBlockUser", agent.name()).isFalse();
        }
        verifyNoInteractions(toolExecutor);
    }
}
