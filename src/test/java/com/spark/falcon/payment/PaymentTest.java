package com.spark.falcon.payment;

import com.spark.falcon.payment.entity.Payment;
import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentFinancialPurpose;
import com.spark.falcon.payment.entity.PaymentSourceModule;
import com.spark.falcon.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTest {

    @Test
    void createsConfirmedCustomerSalePaymentWithSourceReference() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        Payment payment = customerPayment(now);

        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
        assertEquals(51L, payment.getCustomerId());
        assertEquals(901L, payment.getSourceTransactionId());
        assertEquals(new BigDecimal("100.0000"), payment.getAmount());
        assertEquals("attachment/ref", payment.getAttachmentReference());
    }

    @Test
    void preservesCapturedPaymentDateSeparatelyFromConfirmationTime() {
        Instant paymentAt = Instant.parse("2026-08-28T10:15:00Z");
        Instant confirmedAt = Instant.parse("2026-08-29T00:00:00Z");
        Payment payment = Payment.confirmedCustomerPayment(
                1L, 2L, 51L, PaymentDirection.INFLOW, new BigDecimal("100"), "BDT",
                6L, "Card", "CARD", false, "TX-1", "ACCOUNT-1",
                null, null, null, PaymentSourceModule.PAYMENT,
                PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION, 901L, 10L,
                "PAY-902", "attachment/ref", "Due payment", null, null, paymentAt, confirmedAt);

        assertEquals(paymentAt, payment.getCreatedAt());
        assertEquals(confirmedAt, payment.getConfirmedAt());
    }

    @Test
    void confirmedCustomerPaymentCanOnlyBeReversedOnce() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        Payment payment = customerPayment(now);
        payment.markReversed(700L, now.plusSeconds(10));

        assertEquals(PaymentStatus.REVERSED, payment.getStatus());
        assertThrows(IllegalStateException.class,
                () -> payment.markReversed(701L, now.plusSeconds(20)));
    }

    @Test
    void nonCashPaymentRejectsCashMovementLink() {
        Payment payment = customerPayment(Instant.parse("2026-08-29T00:00:00Z"));
        assertThrows(IllegalStateException.class, () -> payment.attachCashMovement(99L));
    }

    private Payment customerPayment(Instant now) {
        return Payment.confirmedCustomerPayment(
                1L, 2L, 51L, PaymentDirection.INFLOW, new BigDecimal("100"), "BDT",
                6L, "Card", "CARD", false, "TX-1", "ACCOUNT-1",
                null, null, null, PaymentSourceModule.SELL,
                PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION, 901L, 10L,
                "PAY-901", "attachment/ref", "Sale payment", null, null, now);
    }
}
