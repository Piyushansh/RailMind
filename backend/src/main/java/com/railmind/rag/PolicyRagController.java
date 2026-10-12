package com.railmind.rag;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyRagController {

    private final PolicyRagService policyRagService;

    public PolicyRagController(PolicyRagService policyRagService) {
        this.policyRagService = policyRagService;
    }

    public record AskRequest(@NotBlank String question) {}

    /** Requires auth (not because the data is sensitive, but because each call is a real LLM + embedding
     * request with a cost, and the default `anyRequest().authenticated()` rule already covers it - no
     * separate SecurityConfig entry needed). */
    @PostMapping("/ask")
    public PolicyRagService.PolicyAnswer ask(@jakarta.validation.Valid @RequestBody AskRequest request) {
        return policyRagService.answer(request.question());
    }
}
