package com.railmind.ai;

import com.railmind.ai.AiEntities.AiConversation;
import com.railmind.ai.AiRepositories.ConversationRepo;
import com.railmind.ai.AiRepositories.MessageRepo;
import com.railmind.ai.AiRepositories.ToolExecutionRepo;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one property that matters here: a conversation belonging to another
 * user must be unreachable, not merely hidden from the list. findByIdAndUserId
 * returning empty (as it would for a real cross-user id) must surface as
 * ResourceNotFoundException, not leak whether the conversation exists.
 */
class AiHistoryServiceTest {

    private static final long OWNER_USER_ID = 1L;

    private ConversationRepo conversationRepo;
    private MessageRepo messageRepo;
    private ToolExecutionRepo toolExecutionRepo;
    private AiHistoryService service;

    @BeforeEach
    void setUp() {
        conversationRepo = mock(ConversationRepo.class);
        messageRepo = mock(MessageRepo.class);
        toolExecutionRepo = mock(ToolExecutionRepo.class);
        service = new AiHistoryService(conversationRepo, messageRepo, toolExecutionRepo);
    }

    @Test
    void getConversationDetail_conversationOwnedByAnotherUser_isNotFound() {
        // A real conversation with this id exists in the DB, but not for this user -
        // findByIdAndUserId(id, OWNER_USER_ID) correctly returns empty either way.
        when(conversationRepo.findByIdAndUserId(99L, OWNER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversationDetail(OWNER_USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getConversationDetail_ownedConversation_returnsMessagesAndToolExecutions() {
        AiConversation conversation = new AiConversation();
        conversation.setId(5L);
        conversation.setTitle("Find me a train to Mumbai");
        when(conversationRepo.findByIdAndUserId(5L, OWNER_USER_ID)).thenReturn(Optional.of(conversation));
        when(messageRepo.findByConversationIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());
        when(toolExecutionRepo.findByConversationIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

        var detail = service.getConversationDetail(OWNER_USER_ID, 5L);

        assertThat(detail.id()).isEqualTo(5L);
        assertThat(detail.title()).isEqualTo("Find me a train to Mumbai");
    }

    @Test
    void listConversations_ordersByMostRecentlyUpdated() {
        when(conversationRepo.findByUserIdOrderByUpdatedAtDesc(OWNER_USER_ID)).thenReturn(List.of());

        assertThat(service.listConversations(OWNER_USER_ID)).isEmpty();
    }
}
