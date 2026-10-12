package com.railmind.cancellation;

import com.railmind.auth.CurrentUser;
import com.railmind.cancellation.dto.CancellationDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cancellations")
public class CancellationController {

    private final CancellationService cancellationService;
    private final CurrentUser currentUser;

    public CancellationController(CancellationService cancellationService, CurrentUser currentUser) {
        this.cancellationService = cancellationService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public RefundDto cancel(@Valid @RequestBody CancelRequest request, Authentication auth) {
        return cancellationService.cancel(currentUser.resolveId(auth), request);
    }
}
