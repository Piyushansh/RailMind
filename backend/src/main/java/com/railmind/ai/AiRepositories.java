package com.railmind.ai;

import com.railmind.ai.AiEntities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiRepositories {
    interface ConversationRepo extends JpaRepository<AiConversation, Long> {
        Optional<AiConversation> findByIdAndUserId(Long id, Long userId);
        List<AiConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    }
    interface MessageRepo extends JpaRepository<AiMessage, Long> {
        List<AiMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    }
    interface ToolExecutionRepo extends JpaRepository<AiToolExecution, Long> {
        List<AiToolExecution> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
        List<AiToolExecution> findByConversation_User_IdOrderByCreatedAtDesc(Long userId);
    }
}
