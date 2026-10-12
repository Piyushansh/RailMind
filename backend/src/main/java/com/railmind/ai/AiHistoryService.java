package com.railmind.ai;

import com.railmind.ai.AiRepositories.ConversationRepo;
import com.railmind.ai.AiRepositories.MessageRepo;
import com.railmind.ai.AiRepositories.ToolExecutionRepo;
import com.railmind.ai.dto.AiDtos.*;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side of AI memory: list a user's own conversations and pull full
 * detail (messages + tool execution trail) for one of them. Every query is
 * scoped by the authenticated user's id - findByIdAndUserId returning empty
 * for a conversation that belongs to someone else is what actually enforces
 * "User A can never read User B's AI history", not a check in the
 * controller.
 */
@Service
public class AiHistoryService {

    private final ConversationRepo conversationRepo;
    private final MessageRepo messageRepo;
    private final ToolExecutionRepo toolExecutionRepo;

    public AiHistoryService(ConversationRepo conversationRepo, MessageRepo messageRepo, ToolExecutionRepo toolExecutionRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.toolExecutionRepo = toolExecutionRepo;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> listConversations(Long userId) {
        return conversationRepo.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ConversationSummaryDto(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailDto getConversationDetail(Long userId, Long conversationId) {
        AiEntities.AiConversation conversation = conversationRepo.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));

        List<MessageDto> messages = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        List<ToolExecutionDto> toolExecutions = toolExecutionRepo.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(t -> new ToolExecutionDto(t.getId(), t.getAgentName(), t.getToolName(), t.getToolInput(), t.getToolOutput(), t.isSuccess(), t.getCreatedAt()))
                .toList();

        return new ConversationDetailDto(conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                conversation.getUpdatedAt(), messages, toolExecutions);
    }
}
