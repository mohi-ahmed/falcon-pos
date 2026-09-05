package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseCommand(
        Long ownerId,
        Long branchId,
        Long supplierId,
        LocalDate purchaseDate,
        String supplierInvoiceReference,
        String notes,
        String attachmentReference,
        List<PurchaseItemCommand> items,
        BigDecimal orderTax,
        BigDecimal shippingCharges,
        BigDecimal otherCharges,
        BigDecimal discount,
        BigDecimal paidAmount,
        Long paymentMethodId,
        String transactionReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        String idempotencyKey) {
}
