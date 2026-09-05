package com.spark.falcon.shared.export;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record ExportDocument(
        String title,
        String businessName,
        String branchName,
        ZoneId zoneId,
        Map<String, String> filters,
        List<ExportColumn> columns,
        ExportRowSource rows
) {
    public ExportDocument {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Export title is required.");
        businessName = businessName == null ? "" : businessName;
        branchName = branchName == null ? "" : branchName;
        zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("At least one export column is required.");
        if (rows == null) throw new IllegalArgumentException("Export rows are required.");
    }
}
