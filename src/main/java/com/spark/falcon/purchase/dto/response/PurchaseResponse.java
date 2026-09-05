package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        Long id,
        Long businessId,
        Long branchId,
        Long supplierId,
        LocalDate purchaseDate,
        String supplierInvoiceReference,
        String notes,
        String attachmentReference,
        PurchaseStatus status,
        PurchasePaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal itemTaxTotal,
        BigDecimal itemDiscountTotal,
        BigDecimal orderTax,
        BigDecimal shippingCharges,
        BigDecimal otherCharges,
        BigDecimal discount,
        BigDecimal totalPayable,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        BigDecimal changeAmount,
        BigDecimal returnedAmount,
        Long createdByActorId,
        Instant confirmedAt,
        Instant createdAt,
        List<PurchaseItemResponse> items) {
}
