package com.railmind.rag.dto;

import com.railmind.rag.PolicyCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class PolicyDocumentDtos {

    public record IngestPolicyDocumentRequest(
            @NotBlank String documentName,
            @NotNull PolicyCategory category,
            @NotBlank String rawText
    ) {}

    public record PolicyDocumentDto(Long id, String documentName, PolicyCategory category, String sourceType,
                                     int chunkCount, Instant createdAt) {}
}
