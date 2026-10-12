package com.railmind.cancellation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The ONLY place refund amounts are calculated. This is a deterministic policy
 * engine, not the AI - the AI assistant may explain the result of calculate()
 * but must never compute or state a refund figure on its own (see AI module).
 */
@Service
public class RefundPolicyService {

    public record RefundQuote(BigDecimal originalFare, BigDecimal deductionPercent, BigDecimal refundAmount, String explanation) {}

    private final double fullRefundHoursBefore;
    private final double partialRefundHoursBefore;
    private final double partialDeductionPercent;
    private final double lateDeductionPercent;

    public RefundPolicyService(
            @Value("${railmind.refund.full-refund-hours-before}") double fullRefundHoursBefore,
            @Value("${railmind.refund.partial-refund-hours-before}") double partialRefundHoursBefore,
            @Value("${railmind.refund.partial-refund-deduction-percent}") double partialDeductionPercent,
            @Value("${railmind.refund.late-cancellation-deduction-percent}") double lateDeductionPercent
    ) {
        this.fullRefundHoursBefore = fullRefundHoursBefore;
        this.partialRefundHoursBefore = partialRefundHoursBefore;
        this.partialDeductionPercent = partialDeductionPercent;
        this.lateDeductionPercent = lateDeductionPercent;
    }

    /**
     * Full refund regardless of timing - used only when RailMind itself caused the
     * disruption (delay/cancellation) that forces a rebooking, not when the user
     * chooses to cancel. This is still centralized here (never computed ad hoc
     * elsewhere) so RefundPolicyService remains the single source of truth for
     * every refund figure in the system, deterministic either way.
     */
    public RefundQuote fullRefund(java.math.BigDecimal fare, String reason) {
        return new RefundQuote(fare, BigDecimal.ZERO, fare,
                "Full refund: " + reason + ". No cancellation charge applies since this was not the passenger's choice.");
    }

    public RefundQuote calculate(BigDecimal fare, double hoursBeforeDeparture) {
        BigDecimal deductionPercent;
        String explanation;

        if (hoursBeforeDeparture >= fullRefundHoursBefore) {
            deductionPercent = BigDecimal.ZERO;
            explanation = "Cancelled " + fmt(hoursBeforeDeparture) + "h before departure (>= " + (int) fullRefundHoursBefore
                    + "h): full refund, no deduction.";
        } else if (hoursBeforeDeparture >= partialRefundHoursBefore) {
            deductionPercent = BigDecimal.valueOf(partialDeductionPercent);
            explanation = "Cancelled " + fmt(hoursBeforeDeparture) + "h before departure (between " + (int) partialRefundHoursBefore
                    + "h and " + (int) fullRefundHoursBefore + "h): " + (int) partialDeductionPercent + "% cancellation charge applies.";
        } else if (hoursBeforeDeparture >= 0) {
            deductionPercent = BigDecimal.valueOf(lateDeductionPercent);
            explanation = "Cancelled only " + fmt(hoursBeforeDeparture) + "h before departure (< " + (int) partialRefundHoursBefore
                    + "h): " + (int) lateDeductionPercent + "% cancellation charge applies.";
        } else {
            deductionPercent = BigDecimal.valueOf(100);
            explanation = "Departure has already passed: no refund is payable.";
        }

        BigDecimal retained = fare.multiply(deductionPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal refund = fare.subtract(retained).max(BigDecimal.ZERO);
        return new RefundQuote(fare, deductionPercent, refund, explanation);
    }

    private String fmt(double hours) {
        return BigDecimal.valueOf(hours).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
