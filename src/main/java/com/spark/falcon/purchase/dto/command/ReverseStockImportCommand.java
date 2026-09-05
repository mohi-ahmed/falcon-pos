package com.spark.falcon.purchase.dto.command;

public record ReverseStockImportCommand(Long ownerId, Long stockImportBatchId, String reason) {
}
