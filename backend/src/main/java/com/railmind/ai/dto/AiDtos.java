package com.railmind.ai.dto;

import jakarta.validation.constraints.NotBlank;

public class AiDtos {

    public record ChatRequest(
            Long conversationId,
            @NotBlank String message
    ) {}

    public record ChatResponse(
            Long conversationId,
            String reply,
            boolean mockMode,
            String provider
    ) {}

    public record ConversationSummaryDto(
            Long id,
            String title,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public record MessageDto(
            Long id,
            String role,
            String content,
            java.time.Instant createdAt
    ) {}

    public record ToolExecutionDto(
            Long id,
            String agentName,
            String toolName,
            String toolInput,
            String toolOutput,
            boolean success,
            java.time.Instant createdAt
    ) {}

    public record ConversationDetailDto(
            Long id,
            String title,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            java.util.List<MessageDto> messages,
            java.util.List<ToolExecutionDto> toolExecutions
    ) {}
}
