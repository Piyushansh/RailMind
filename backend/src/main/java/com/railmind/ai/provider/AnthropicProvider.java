package com.railmind.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Secondary/optional provider, kept from the original implementation and
 * adapted to the shared AIProvider interface. Not the default (OpenAI is),
 * but selectable via AI_PROVIDER=anthropic if the project owner has an
 * Anthropic key instead of/alongside an OpenAI one.
 */
@Component
public class AnthropicProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicProvider(
            @Value("${railmind.ai.anthropic.base-url}") String baseUrl,
            @Value("${railmind.ai.anthropic.api-key}") String apiKey,
            @Value("${railmind.ai.anthropic.model}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String getName() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AssistantTurn sendMessage(String systemPrompt, List<Turn> history, List<ToolSpec> tools) {
        ArrayNode messages = mapper.createArrayNode();

        for (Turn turn : history) {
            if (turn instanceof UserTextTurn u) {
                messages.add(simpleTextMessage("user", u.text()));
            } else if (turn instanceof AssistantTurn a) {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "assistant");
                ArrayNode content = mapper.createArrayNode();
                if (a.text() != null && !a.text().isEmpty()) {
                    ObjectNode textBlock = mapper.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", a.text());
                    content.add(textBlock);
                }
                for (ToolCall tc : a.toolCalls()) {
                    ObjectNode toolUse = mapper.createObjectNode();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.id());
                    toolUse.put("name", tc.name());
                    toolUse.set("input", tc.arguments());
                    content.add(toolUse);
                }
                msg.set("content", content);
                messages.add(msg);
            } else if (turn instanceof ToolResultTurn t) {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "user");
                ArrayNode content = mapper.createArrayNode();
                for (ToolResult r : t.results()) {
                    ObjectNode block = mapper.createObjectNode();
                    block.put("type", "tool_result");
                    block.put("tool_use_id", r.toolCallId());
                    block.put("content", r.content());
                    content.add(block);
                }
                msg.set("content", content);
                messages.add(msg);
            }
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.set("messages", messages);
        body.set("tools", toAnthropicTools(tools));

        JsonNode response = webClient.post()
                .uri("")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("content")) {
            throw new IllegalStateException("Anthropic API returned an unexpected response.");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode block : response.get("content")) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                text.append(block.path("text").asText());
            } else if ("tool_use".equals(type)) {
                String id = block.path("id").asText(UUID.randomUUID().toString());
                toolCalls.add(new ToolCall(id, block.path("name").asText(), block.path("input")));
            }
        }
        return new AssistantTurn(text.toString(), toolCalls);
    }

    private ObjectNode simpleTextMessage(String role, String text) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        ArrayNode content = mapper.createArrayNode();
        ObjectNode block = mapper.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        content.add(block);
        msg.set("content", content);
        return msg;
    }

    private ArrayNode toAnthropicTools(List<ToolSpec> tools) {
        ArrayNode arr = mapper.createArrayNode();
        for (ToolSpec spec : tools) {
            ObjectNode t = mapper.createObjectNode();
            t.put("name", spec.name());
            t.put("description", spec.description());
            t.set("input_schema", spec.parametersSchema());
            arr.add(t);
        }
        return arr;
    }
}
