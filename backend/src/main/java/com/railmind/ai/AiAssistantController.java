package com.railmind.ai;

import com.railmind.ai.dto.AiDtos.*;
import com.railmind.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private final AiOrchestratorService aiOrchestratorService;
    private final AiHistoryService aiHistoryService;
    private final CurrentUser currentUser;

    public AiAssistantController(AiOrchestratorService aiOrchestratorService, AiHistoryService aiHistoryService,
                                  CurrentUser currentUser) {
        this.aiOrchestratorService = aiOrchestratorService;
        this.aiHistoryService = aiHistoryService;
        this.currentUser = currentUser;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        Long userId = currentUser.resolveId(auth);
        return aiOrchestratorService.chat(userId, request.conversationId(), request.message());
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryDto> listConversations(Authentication auth) {
        return aiHistoryService.listConversations(currentUser.resolveId(auth));
    }

    @GetMapping("/conversations/{id}")
    public ConversationDetailDto getConversation(@PathVariable Long id, Authentication auth) {
        return aiHistoryService.getConversationDetail(currentUser.resolveId(auth), id);
    }
}
