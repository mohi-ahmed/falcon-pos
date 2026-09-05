package com.spark.falcon.payment.dto;

import java.math.BigDecimal;

public record InitialSupplierPaymentCommand(
        Long businessId,
        Long branchId,
        Long ownerId,
        Long supplierId,
        Long purchaseId,
        BigDecimal amount,
        Long paymentMethodId,
        String transactionReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        String notes
) {
}
