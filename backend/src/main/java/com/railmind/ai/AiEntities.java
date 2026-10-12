package com.railmind.ai;

import com.railmind.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

public class AiEntities {

    @Getter @Setter
    @Entity
    @Table(name = "ai_conversations")
    public static class AiConversation {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        private String title;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

        @PreUpdate
        void onUpdate() { updatedAt = Instant.now(); }
    }

    @Getter @Setter
    @Entity
    @Table(name = "ai_messages")
    public static class AiMessage {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "conversation_id", nullable = false)
        private AiConversation conversation;

        @Column(nullable = false)
        private String role; // USER, ASSISTANT, TOOL

        @Column(nullable = false, columnDefinition = "TEXT")
        private String content;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    @Getter @Setter
    @Entity
    @Table(name = "ai_tool_executions")
    public static class AiToolExecution {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "conversation_id", nullable = false)
        private AiConversation conversation;

        @Column(name = "tool_name", nullable = false)
        private String toolName;

        @Column(name = "agent_name")
        private String agentName;

        @Column(name = "tool_input", columnDefinition = "TEXT")
        private String toolInput;

        @Column(name = "tool_output", columnDefinition = "TEXT")
        private String toolOutput;

        @Column(nullable = false)
        private boolean success;

        @Column(name = "correlation_id")
        private String correlationId;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
