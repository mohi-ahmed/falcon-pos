package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SalePaymentStatus;
import com.spark.falcon.sale.entity.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record SaleInvoiceResponse(
        Long saleId,
        Long branchId,
        String businessName,
        String branchName,
        String branchCode,
        String currency,
        String businessLogoReference,
        String branchPhone,
        String branchEmail,
        String branchAddress,
        String vatBinNumber,
        String customerName,
        String customerPhone,
        String customerEmail,
        SaleStatus saleStatus,
        SalePaymentStatus paymentStatus,
        ZonedDateTime issuedAt,
        LocalDate dueDate,
        List<SaleInvoiceItemResponse> items,
        BigDecimal grossItemTotal,
        BigDecimal itemDiscountTotal,
        BigDecimal itemTaxTotal,
        BigDecimal orderDiscount,
        BigDecimal shippingCharge,
        BigDecimal otherCharge,
        BigDecimal totalPayable,
        BigDecimal paidAmount,
        BigDecimal dueAmount,
        BigDecimal changeAmount,
        BigDecimal returnedAmount,
        List<SaleInvoicePaymentLine> payments,
        String receiptFooter
) {
}
