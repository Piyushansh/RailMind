package com.railmind.rag;

import com.railmind.ai.provider.AIProvider;
import com.railmind.ai.provider.AIProvider.AssistantTurn;
import com.railmind.ai.provider.AIProvider.Turn;
import com.railmind.ai.provider.AIProvider.UserTextTurn;
import com.railmind.ai.provider.AIProviderResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The "Policy/RAG Agent": answers railway-policy questions strictly from
 * retrieved chunks, always with sources, and returns a safe fixed response -
 * never a model guess - when retrieval comes back empty. This is what
 * ToolExecutor's policySearch tool calls under the hood, and is also usable
 * standalone via PolicyRagController for a plain policy-Q&A endpoint that
 * doesn't need a full conversational session.
 *
 * Every call is logged to policy_queries (question, grounded, top similarity)
 * so admin analytics can report real RAG usage/grounding-rate rather than
 * nothing at all.
 */
@Service
public class PolicyRagService {

    private static final String INSUFFICIENT_INFO_MESSAGE =
            "I don't have enough verified policy information to answer that confidently. "
                    + "Please check the official RailMind policy pages or contact support for a definitive answer.";

    private final PolicyRetrievalService retrievalService;
    private final AIProviderResolver aiProviderResolver;
    private final PolicyQueryLogRepository queryLogRepository;

    public PolicyRagService(PolicyRetrievalService retrievalService, AIProviderResolver aiProviderResolver,
                             PolicyQueryLogRepository queryLogRepository) {
        this.retrievalService = retrievalService;
        this.aiProviderResolver = aiProviderResolver;
        this.queryLogRepository = queryLogRepository;
    }

    public record Source(String chunkId, String documentName, String category, String section, Integer page, double similarity) {}

    public record PolicyAnswer(String answer, boolean grounded, List<Source> sources) {}

    @Transactional
    public PolicyAnswer answer(String question) {
        List<PolicyRetrievalService.RetrievedChunk> chunks = retrievalService.retrieve(question);
        if (chunks.isEmpty()) {
            logQuery(question, false, null);
            return new PolicyAnswer(INSUFFICIENT_INFO_MESSAGE, false, List.of());
        }

        String context = buildContext(chunks);
        String systemPrompt = """
                You are RailMind's railway policy assistant. Answer the user's question using ONLY the
                policy excerpts provided below. Do not use outside knowledge. If the excerpts don't fully
                answer the question, say so explicitly rather than filling gaps with assumptions. Keep the
                answer concise and reference which excerpt(s) you used by their [chunk_id].

                POLICY EXCERPTS:
                %s
                """.formatted(context);

        AIProvider provider = aiProviderResolver.resolve();
        AssistantTurn turn = provider.sendMessage(systemPrompt, List.of((Turn) new UserTextTurn(question)), List.of());

        List<Source> sources = chunks.stream()
                .map(c -> new Source(c.chunkId(), c.documentName(), c.category().name(), c.section(), c.page(), c.similarity()))
                .toList();
        logQuery(question, true, chunks.get(0).similarity());
        return new PolicyAnswer(turn.text(), true, sources);
    }

    private void logQuery(String question, boolean grounded, Double topSimilarity) {
        PolicyQueryLog log = new PolicyQueryLog();
        log.setQuestion(question.length() > 500 ? question.substring(0, 500) : question);
        log.setGrounded(grounded);
        log.setTopSimilarity(topSimilarity != null ? BigDecimal.valueOf(topSimilarity) : null);
        queryLogRepository.save(log);
    }

    private String buildContext(List<PolicyRetrievalService.RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (PolicyRetrievalService.RetrievedChunk c : chunks) {
            sb.append("[chunk_id: ").append(c.chunkId()).append(", document: ").append(c.documentName())
                    .append(", category: ").append(c.category())
                    .append(c.section() != null ? ", section: " + c.section() : "")
                    .append("]\n").append(c.content()).append("\n\n");
        }
        return sb.toString();
    }
}
