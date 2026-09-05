package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.CustomerPaymentSettlementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerPaymentCommand(
        Long ownerId,
        Long branchId,
        Long customerId,
        Instant paymentDateTime,
        BigDecimal amount,
        Long paymentMethodId,
        String transactionReference,
        String accountReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        List<CustomerPaymentAllocationCommand> allocations,
        boolean automaticOldestDueFirst,
        CustomerPaymentSettlementType excessSettlementType,
        String idempotencyKey,
        String notes,
        String attachmentReference
) {
}
