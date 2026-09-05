package com.spark.falcon.purchase.dto.command;

import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnSettlementType;

import java.time.LocalDate;
import java.util.List;

public record PurchaseReturnCommand(
        Long ownerId,
        Long branchId,
        Long purchaseId,
        String referenceNumber,
        LocalDate returnDate,
        String notes,
        PurchaseReturnSettlementType settlementType,
        Long paymentMethodId,
        String transactionReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        List<PurchaseReturnItemCommand> items,
        String idempotencyKey) {
}
