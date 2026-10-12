package com.railmind.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Provider-agnostic contract for "talk to an LLM with tool calling". Nothing
 * outside this package knows whether it's OpenAI, Anthropic, or the mock -
 * AiOrchestratorService only deals in Turn/ToolSpec/ToolCall, never in a
 * vendor's wire format. This is what makes swapping providers (or adding a
 * third) a matter of adding one new class, not touching the orchestrator.
 */
public interface AIProvider {

    /** Stable id used in railmind.ai.provider (e.g. "openai", "anthropic", "mock"). */
    String getName();

    /** False if required config (e.g. an API key) is missing - the resolver falls back to mock in that case. */
    boolean isConfigured();

    /** One inference call: given the conversation so far, return the model's next turn (text and/or tool calls). */
    AssistantTurn sendMessage(String systemPrompt, List<Turn> history, List<ToolSpec> tools);

    // ---- provider-agnostic conversation model ----

    sealed interface Turn permits UserTextTurn, AssistantTurn, ToolResultTurn {}

    record UserTextTurn(String text) implements Turn {}

    record AssistantTurn(String text, List<ToolCall> toolCalls) implements Turn {
        public boolean requestsTools() { return toolCalls != null && !toolCalls.isEmpty(); }
    }

    record ToolResultTurn(List<ToolResult> results) implements Turn {}

    record ToolCall(String id, String name, JsonNode arguments) {}

    record ToolResult(String toolCallId, String content) {}

    record ToolSpec(String name, String description, JsonNode parametersSchema) {}
}
