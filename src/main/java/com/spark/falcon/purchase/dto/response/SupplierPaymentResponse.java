package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.SupplierPaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SupplierPaymentResponse(
        Long id,
        Long businessId,
        Long branchId,
        Long supplierId,
        Long paymentMethodId,
        String paymentMethodName,
        String paymentMethodCode,
        boolean cashPayment,
        BigDecimal amount,
        String transactionReference,
        Long cashMovementId,
        SupplierPaymentStatus status,
        Long createdByActorId,
        Instant createdAt,
        List<SupplierPaymentAllocationResponse> allocations) {
}
