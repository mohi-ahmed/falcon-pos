package com.spark.falcon.purchase.dto.command;

public record ConfirmStockImportCommand(Long ownerId, Long stockImportBatchId) {
}
