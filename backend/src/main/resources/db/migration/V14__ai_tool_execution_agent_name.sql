-- Phase 6 (AI Agent Architecture): every tool call is now routed through a
-- named specialist agent (JourneyPlanningAgent, BookingAgent, PolicyRagAgent,
-- DisruptionAgent, TripGuardianAgent - see com.railmind.ai.agents) before
-- ToolExecutor runs it. This column records which agent handled each call,
-- so the audit trail shows not just "what tool ran" but "which agent's
-- permission set it ran under" - useful for confirming, after the fact,
-- that (for example) no tool call from the policy-answering agent ever
-- touched a booking-mutating tool.

ALTER TABLE ai_tool_executions ADD COLUMN agent_name VARCHAR(60);

CREATE INDEX idx_ai_tool_executions_agent ON ai_tool_executions(agent_name);
