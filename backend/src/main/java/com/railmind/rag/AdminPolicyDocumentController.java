package com.railmind.rag;

import com.railmind.auth.CurrentUser;
import com.railmind.rag.PolicyRepositories.PolicyChunkRepository;
import com.railmind.rag.PolicyRepositories.PolicyDocumentRepository;
import com.railmind.rag.dto.PolicyDocumentDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only: add real policy documents to the RAG corpus (real ingestion -
 * chunked and embedded on upload, same pipeline PolicySeedDataRunner uses at
 * startup). Protected both by SecurityConfig's /api/v1/admin/** rule and the
 * explicit @PreAuthorize below.
 */
@RestController
@RequestMapping("/api/v1/admin/policy-documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPolicyDocumentController {

    private final PolicyIngestionService ingestionService;
    private final PolicyDocumentRepository documentRepository;
    private final PolicyChunkRepository chunkRepository;
    private final CurrentUser currentUser;

    public AdminPolicyDocumentController(PolicyIngestionService ingestionService,
                                          PolicyDocumentRepository documentRepository,
                                          PolicyChunkRepository chunkRepository, CurrentUser currentUser) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<PolicyDocumentDto> list() {
        return documentRepository.findAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<PolicyDocumentDto> ingest(@Valid @RequestBody IngestPolicyDocumentRequest request,
                                                      Authentication auth) {
        PolicyDocument doc = ingestionService.ingest(request.documentName(), request.category(), "ADMIN_UPLOAD",
                request.rawText(), currentUser.resolveId(auth));
        return ResponseEntity.ok(toDto(doc));
    }

    private PolicyDocumentDto toDto(PolicyDocument doc) {
        int chunkCount = chunkRepository.findByDocumentId(doc.getId()).size();
        return new PolicyDocumentDto(doc.getId(), doc.getDocumentName(), doc.getCategory(), doc.getSourceType(),
                chunkCount, doc.getCreatedAt());
    }
}
