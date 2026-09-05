package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentFinancialPurpose;
import com.spark.falcon.payment.entity.PaymentPartyType;
import com.spark.falcon.payment.entity.PaymentSourceModule;
import com.spark.falcon.payment.entity.PaymentStatus;
import com.spark.falcon.payment.entity.CustomerPaymentSettlementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentResponse(
        Long id,
        Long businessId,
        Long branchId,
        PaymentPartyType partyType,
        Long customerId,
        Long supplierId,
        PaymentDirection direction,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        CustomerPaymentSettlementType customerSettlementType,
        BigDecimal customerCreditAmount,
        BigDecimal changeAmount,
        String currency,
        Long paymentMethodId,
        String paymentMethodName,
        String paymentMethodCode,
        boolean cashPayment,
        String transactionReference,
        String accountReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        Long cashMovementId,
        PaymentStatus status,
        PaymentSourceModule sourceModule,
        PaymentFinancialPurpose financialPurpose,
        Long sourceTransactionId,
        Long createdByActorId,
        Long confirmedByActorId,
        Instant createdAt,
        Instant confirmedAt,
        Long reversalOfPaymentId,
        Long reversedByPaymentId,
        Instant reversedAt,
        String reversalReason,
        String attachmentReference,
        String notes,
        List<PaymentAllocationResponse> allocations,
        List<PaymentAuditResponse> auditTimeline
) {
}
