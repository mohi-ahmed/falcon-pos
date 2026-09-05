package com.spark.falcon.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPurchaseTraceResponse(
        Long purchaseItemId,
        Long purchaseId,
        Long supplierId,
        LocalDate purchaseDate,
        String supplierInvoiceReference,
        BigDecimal enteredUnitCost,
        BigDecimal baseUnitLandedCost) { }
