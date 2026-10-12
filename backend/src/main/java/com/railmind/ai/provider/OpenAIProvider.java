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

/**
 * Primary AI provider: OpenAI's Chat Completions API with function calling.
 * Talks to POST {base-url}/chat/completions with "tools": [{type:"function", function:{...}}].
 *
 * The API key is read from ${OPENAI_API_KEY} (see application.yml) and is
 * never sent to, or readable by, the frontend - it only ever leaves this
 * process as an outbound Authorization header to api.openai.com.
 */
@Component
public class OpenAIProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAIProvider(
            @Value("${railmind.ai.openai.base-url}") String baseUrl,
            @Value("${railmind.ai.openai.api-key}") String apiKey,
            @Value("${railmind.ai.openai.model}") String model
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
    public AssistantTurn sendMessage(String systemPrompt, List<Turn> history, List<ToolSpec> tools) {
        ArrayNode messages = mapper.createArrayNode();

        ObjectNode systemMsg = mapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        for (Turn turn : history) {
            if (turn instanceof ToolResultTurn resultTurn) {
                for (ToolResult result : resultTurn.results()) {
                    ObjectNode toolMsg = mapper.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", result.toolCallId());
                    toolMsg.put("content", result.content());
                    messages.add(toolMsg);
                }
            } else {
                messages.add(toOpenAiMessage(turn));
            }
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        body.set("tools", toOpenAiTools(tools));
        body.put("tool_choice", "auto");

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            throw new IllegalStateException("OpenAI API returned an unexpected response.");
        }

        JsonNode message = response.get("choices").get(0).get("message");
        String text = message.path("content").isNull() ? "" : message.path("content").asText("");

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            for (JsonNode tc : message.get("tool_calls")) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String argsRaw = tc.path("function").path("arguments").asText("{}");
                JsonNode args;
                try {
                    args = mapper.readTree(argsRaw);
                } catch (Exception e) {
                    args = mapper.createObjectNode();
                }
                toolCalls.add(new ToolCall(id, name, args));
            }
        }

        return new AssistantTurn(text, toolCalls);
    }

    private ObjectNode toOpenAiMessage(Turn turn) {
        if (turn instanceof UserTextTurn u) {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("role", "user");
            msg.put("content", u.text());
            return msg;
        }
        if (turn instanceof AssistantTurn a) {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("role", "assistant");
            if (a.text() != null && !a.text().isEmpty()) {
                msg.put("content", a.text());
            } else {
                msg.putNull("content");
            }
            if (a.requestsTools()) {
                ArrayNode toolCallsNode = mapper.createArrayNode();
                for (ToolCall tc : a.toolCalls()) {
                    ObjectNode tcNode = mapper.createObjectNode();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode function = mapper.createObjectNode();
                    function.put("name", tc.name());
                    function.put("arguments", tc.arguments().toString());
                    tcNode.set("function", function);
                    toolCallsNode.add(tcNode);
                }
                msg.set("tool_calls", toolCallsNode);
            }
            return msg;
        }
        if (turn instanceof ToolResultTurn t) {
            // OpenAI expects one "tool" role message PER result; caller flattens via toOpenAiMessages below.
            // Kept here only to satisfy the sealed interface - see sendMessage's use of expandToolResults.
            throw new IllegalStateException("ToolResultTurn must be expanded before reaching toOpenAiMessage");
        }
        throw new IllegalStateException("Unknown turn type: " + turn);
    }

    // ---- tool schema translation ----

    private ArrayNode toOpenAiTools(List<ToolSpec> tools) {
        ArrayNode arr = mapper.createArrayNode();
        for (ToolSpec spec : tools) {
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("type", "function");
            ObjectNode function = mapper.createObjectNode();
            function.put("name", spec.name());
            function.put("description", spec.description());
            function.set("parameters", spec.parametersSchema());
            wrapper.set("function", function);
            arr.add(wrapper);
        }
        return arr;
    }
}
