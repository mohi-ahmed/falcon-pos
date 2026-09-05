package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnSettlementType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseReturnResponse(
        Long id,
        Long businessId,
        Long branchId,
        Long purchaseId,
        Long supplierId,
        String referenceNumber,
        LocalDate returnDate,
        String notes,
        PurchaseReturnSettlementType settlementType,
        Long paymentMethodId,
        String transactionReference,
        BigDecimal totalReturnAmount,
        BigDecimal supplierDueReduction,
        BigDecimal supplierCreditAmount,
        BigDecimal refundAmount,
        Long cashMovementId,
        PurchaseReturnStatus status,
        Long createdByActorId,
        Instant confirmedAt,
        Instant createdAt,
        List<PurchaseReturnItemResponse> items) {
}
