package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;
import java.util.List;

public record SupplierPaymentCommand(
        Long ownerId,
        Long branchId,
        Long supplierId,
        Long paymentMethodId,
        BigDecimal amount,
        String transactionReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        List<SupplierPaymentAllocationCommand> allocations,
        boolean automaticOldestDueFirst,
        String idempotencyKey,
        String notes) {
}
