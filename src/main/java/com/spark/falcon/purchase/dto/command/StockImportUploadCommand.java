package com.spark.falcon.purchase.dto.command;

import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;

public record StockImportUploadCommand(
        Long ownerId,
        Long branchId,
        StockImportPurpose importPurpose,
        String fileName,
        byte[] fileContent,
        String idempotencyKey) {
}
