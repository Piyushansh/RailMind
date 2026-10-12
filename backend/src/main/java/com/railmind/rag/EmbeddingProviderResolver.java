package com.railmind.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Same resolution pattern as ai.provider.AIProviderResolver: pick the
 * configured provider if it's actually usable (has credentials), otherwise
 * fall back to the always-available deterministic one, rather than failing
 * requests outright.
 */
@Component
public class EmbeddingProviderResolver {

    private final Map<String, EmbeddingProvider> providersByName;
    private final String configuredProviderName;
    private final HashingEmbeddingProvider fallback;

    public EmbeddingProviderResolver(List<EmbeddingProvider> providers, HashingEmbeddingProvider fallback,
                                      @Value("${railmind.rag.embedding-provider:hashing}") String configuredProviderName) {
        this.providersByName = providers.stream().collect(Collectors.toMap(EmbeddingProvider::getName, p -> p));
        this.fallback = fallback;
        this.configuredProviderName = configuredProviderName;
    }

    public EmbeddingProvider resolve() {
        EmbeddingProvider configured = providersByName.get(configuredProviderName);
        if (configured != null && configured.isConfigured()) {
            return configured;
        }
        return fallback;
    }
}
