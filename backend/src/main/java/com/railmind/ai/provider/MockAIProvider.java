package com.railmind.ai.provider;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic fallback provider - always "configured" (there's nothing to
 * configure), never calls out to the network, and never requests a tool call.
 * This is what keeps the app fully runnable with zero API keys, satisfying
 * the "never leave the app in a broken state" requirement.
 */
@Component
public class MockAIProvider implements AIProvider {

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public AssistantTurn sendMessage(String systemPrompt, List<Turn> history, List<ToolSpec> tools) {
        String lastUserText = history.stream()
                .filter(t -> t instanceof UserTextTurn)
                .map(t -> ((UserTextTurn) t).text())
                .reduce((first, second) -> second)
                .orElse("");

        String reply = "[mock AI mode - no real LLM configured] I received: \"" + lastUserText
                + "\". Set AI_PROVIDER=openai (or anthropic) and the matching API key to enable "
                + "the real tool-calling agent with " + tools.size() + " available tools.";

        return new AssistantTurn(reply, List.of());
    }
}
