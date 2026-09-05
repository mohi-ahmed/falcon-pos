package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SalePaymentStatus;
import com.spark.falcon.sale.entity.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SaleResponse(
        Long id,
        Long businessId,
        Long branchId,
        Long customerId,
        String customerName,
        SaleStatus status,
        SalePaymentStatus paymentStatus,
        BigDecimal grossItemTotal,
        BigDecimal itemDiscountTotal,
        BigDecimal itemTaxTotal,
        BigDecimal itemPayableTotal,
        BigDecimal orderDiscount,
        BigDecimal shippingCharge,
        BigDecimal otherCharge,
        BigDecimal totalPayable,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        BigDecimal changeAmount,
        BigDecimal returnedAmount,
        BigDecimal cogsTotal,
        LocalDate dueDate,
        String notes,
        Long createdByActorId,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt,
        List<SaleItemResponse> items
) {
    public SaleResponse(
            Long id,
            Long businessId,
            Long branchId,
            Long customerId,
            SaleStatus status,
            SalePaymentStatus paymentStatus,
            BigDecimal grossItemTotal,
            BigDecimal itemDiscountTotal,
            BigDecimal itemTaxTotal,
            BigDecimal itemPayableTotal,
            BigDecimal orderDiscount,
            BigDecimal shippingCharge,
            BigDecimal otherCharge,
            BigDecimal totalPayable,
            BigDecimal paidAmount,
            BigDecimal dueAmount,
            BigDecimal changeAmount,
            BigDecimal returnedAmount,
            BigDecimal cogsTotal,
            LocalDate dueDate,
            String notes,
            Long createdByActorId,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt,
            List<SaleItemResponse> items
    ) {
        this(id, businessId, branchId, customerId, null, status, paymentStatus, grossItemTotal, itemDiscountTotal,
                itemTaxTotal, itemPayableTotal, orderDiscount, shippingCharge, otherCharge, totalPayable, paidAmount,
                dueAmount, changeAmount, returnedAmount, cogsTotal, dueDate, notes, createdByActorId, confirmedAt,
                createdAt, updatedAt, items);
    }
}
