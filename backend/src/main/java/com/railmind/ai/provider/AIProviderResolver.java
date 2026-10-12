package com.railmind.ai.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the AIProvider named by railmind.ai.provider (env AI_PROVIDER). If
 * that provider isn't configured (e.g. "openai" selected but OPENAI_API_KEY
 * is blank), falls back to MockAIProvider rather than failing every request -
 * this is the concrete mechanism behind "never leave the app broken because
 * an external AI service is unavailable".
 */
@Component
public class AIProviderResolver {

    private final Map<String, AIProvider> providersByName;
    private final MockAIProvider mockAIProvider;
    private final String configuredProviderName;

    public AIProviderResolver(List<AIProvider> providers, MockAIProvider mockAIProvider,
                               @Value("${railmind.ai.provider}") String configuredProviderName) {
        this.providersByName = providers.stream()
                .collect(Collectors.toMap(AIProvider::getName, Function.identity()));
        this.mockAIProvider = mockAIProvider;
        this.configuredProviderName = configuredProviderName;
    }

    public AIProvider resolve() {
        AIProvider selected = providersByName.get(configuredProviderName == null ? "" : configuredProviderName.toLowerCase());
        if (selected != null && selected.isConfigured()) {
            return selected;
        }
        return mockAIProvider;
    }

    public boolean isRunningInMockMode() {
        return resolve() == mockAIProvider;
    }
}
