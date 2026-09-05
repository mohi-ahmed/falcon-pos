package com.spark.falcon.branch.dto.command;

import java.math.BigDecimal;

public record CreateBranchCommand(
        Long ownerId,
        String idempotencyKey,
        String name,
        String code,
        String country,
        String email,
        String phone,
        String timeZone,
        String currency,
        String address,
        String city,
        String stateDivision,
        String postalCode,
        String vatBinNumber,
        BigDecimal defaultTaxRate,
        Integer lowStockAlertQuantity,
        Integer recordsPerPage,
        String receiptFooter
) {
}