package com.railmind.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Deterministic-enough sandbox payment provider: succeeds by default so the
 * demo flow always works, but lets a caller force a failure (amount ending in
 * .13, an easter-egg any demo/tutorial can trigger deliberately) to exercise
 * the failure path without real money or a real gateway.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    private final SecureRandom random = new SecureRandom();

    @Override
    public ChargeResult charge(Long bookingId, BigDecimal amount, String idempotencyKey) {
        boolean forcedFailure = amount.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.13")) == 0;
        if (forcedFailure) {
            return new ChargeResult(false, null, "Card declined by issuing bank (simulated).");
        }
        return new ChargeResult(true, "MOCK-" + UUID.randomUUID(), null);
    }
}
