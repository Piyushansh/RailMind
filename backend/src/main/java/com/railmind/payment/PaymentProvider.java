package com.railmind.payment;

import java.math.BigDecimal;

/**
 * Abstraction over a real payment gateway. Swap MockPaymentProvider for a real
 * implementation (Stripe/Razorpay/etc.) without touching PaymentService.
 */
public interface PaymentProvider {

    record ChargeResult(boolean success, String providerReference, String failureReason) {}

    ChargeResult charge(Long bookingId, BigDecimal amount, String idempotencyKey);
}
