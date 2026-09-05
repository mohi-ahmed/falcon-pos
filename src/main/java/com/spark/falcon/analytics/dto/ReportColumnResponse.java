package com.spark.falcon.analytics.dto;

import com.spark.falcon.shared.export.ExportColumn;

public record ReportColumnResponse(String key, String heading, ExportColumn.ValueType valueType) {
    public ReportColumnResponse {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Report column key is required.");
        if (heading == null || heading.isBlank()) throw new IllegalArgumentException("Report column heading is required.");
        if (valueType == null) valueType = ExportColumn.ValueType.TEXT;
    }

    public ExportColumn exportColumn() {
        return new ExportColumn(heading, valueType);
    }
}
