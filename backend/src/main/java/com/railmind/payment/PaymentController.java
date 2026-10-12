package com.railmind.payment;

import com.railmind.auth.CurrentUser;
import com.railmind.payment.dto.PaymentDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public PaymentResultDto pay(@Valid @RequestBody PayRequest request, Authentication auth) {
        return paymentService.pay(currentUser.resolveId(auth), request);
    }
}
