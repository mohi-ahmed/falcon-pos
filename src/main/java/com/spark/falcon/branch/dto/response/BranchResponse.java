package com.spark.falcon.branch.dto.response;

import com.spark.falcon.branch.entity.enumtype.BranchStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BranchResponse(
        Long id,
        Long businessId,
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
        String receiptFooter,
        BranchStatus status,
        Instant createdAt
) {
}