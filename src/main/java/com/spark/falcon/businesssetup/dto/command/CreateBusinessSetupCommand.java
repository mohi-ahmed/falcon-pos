package com.spark.falcon.businesssetup.dto.command;
import com.spark.falcon.businesssetup.entity.enumtype.BusinessType;
import java.math.BigDecimal;
public record CreateBusinessSetupCommand(
        Long ownerId, String idempotencyKey, String businessName, String businessCode,
        BusinessType businessType, String businessEmail, String businessPhone,
        String branchName, String branchCode, String branchEmail, String branchPhone,
        String country, String timezone, String currency, String address, String city,
        String stateDivision, String postalCode, String vatBinNumber, BigDecimal defaultTaxRate,
        Integer lowStockAlertQuantity, Integer recordsPerPage, String receiptFooter) {}
