package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SaleReturnSettlementType;
import com.spark.falcon.sale.entity.SaleReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleReturnResponse(
        Long id,
        Long businessId,
        Long branchId,
        Long saleId,
        Long customerId,
        String customerName,
        String referenceNumber,
        SaleReturnSettlementType settlementType,
        BigDecimal totalReturnAmount,
        BigDecimal dueReductionAmount,
        BigDecimal refundAmount,
        BigDecimal customerCreditAmount,
        Long refundPaymentId,
        SaleReturnStatus status,
        String notes,
        Long createdByActorId,
        Instant confirmedAt,
        Instant createdAt,
        List<SaleReturnItemResponse> items
) {
}
