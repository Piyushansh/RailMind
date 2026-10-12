package com.railmind.cancellation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyServiceTest {

    private final RefundPolicyService service = new RefundPolicyService(
            48, // full refund if >= 48h before departure
            4,  // partial refund band down to 4h before departure
            25, // 25% deduction in the partial band
            50  // 50% deduction inside 4h
    );

    @Test
    void fullRefund_whenCancelledWellBeforeDeparture() {
        var quote = service.calculate(new BigDecimal("1000.00"), 72.0);
        assertThat(quote.deductionPercent()).isEqualByComparingTo("0");
        assertThat(quote.refundAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void partialRefund_whenCancelledInMiddleBand() {
        var quote = service.calculate(new BigDecimal("1000.00"), 10.0);
        assertThat(quote.deductionPercent()).isEqualByComparingTo("25");
        assertThat(quote.refundAmount()).isEqualByComparingTo("750.00");
    }

    @Test
    void heavyDeduction_whenCancelledCloseToDeparture() {
        var quote = service.calculate(new BigDecimal("1000.00"), 2.0);
        assertThat(quote.deductionPercent()).isEqualByComparingTo("50");
        assertThat(quote.refundAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void noRefund_whenDepartureAlreadyPassed() {
        var quote = service.calculate(new BigDecimal("1000.00"), -1.0);
        assertThat(quote.deductionPercent()).isEqualByComparingTo("100");
        assertThat(quote.refundAmount()).isEqualByComparingTo("0.00");
    }
}
