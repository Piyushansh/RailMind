package com.railmind.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Real embeddings via OpenAI's /embeddings endpoint (text-embedding-3-small,
 * 1536 dimensions - matches the fixed vector(1536) column in V8__policy_rag.sql).
 * Same WebClient pattern as ai.provider.OpenAIProvider, kept independent of it
 * since embeddings and chat completions are separate OpenAI products.
 */
@Component
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSIONS = 1536;

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIEmbeddingProvider(
            @Value("${railmind.ai.openai.base-url}") String baseUrl,
            @Value("${railmind.ai.openai.api-key}") String apiKey,
            @Value("${railmind.rag.openai.embedding-model:text-embedding-3-small}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode input = mapper.createArrayNode();
        texts.forEach(input::add);
        body.set("input", input);

        JsonNode response = webClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data")) {
            throw new IllegalStateException("OpenAI embeddings API returned an unexpected response.");
        }

        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : response.get("data")) {
            JsonNode embeddingNode = item.get("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
