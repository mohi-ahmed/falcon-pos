package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.entity.enumtype.StockImportStatus;

import java.time.Instant;
import java.util.List;

public record StockImportBatchResponse(
        Long id,
        Long businessId,
        Long branchId,
        StockImportPurpose importPurpose,
        String fileName,
        String fileFingerprint,
        int totalRows,
        int validRows,
        int failedRows,
        int postedMovementCount,
        String errorSummary,
        StockImportStatus status,
        Long uploadedByActorId,
        Long confirmedByActorId,
        Instant uploadedAt,
        Instant confirmedAt,
        Instant reversedAt,
        List<StockImportRowResponse> rows) {
}
