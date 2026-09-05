package com.spark.falcon.analytics.dto;

import java.util.List;
import java.util.Map;

public record ReportPageResponse(
        AnalyticsReportType type,
        List<ReportColumnResponse> columns,
        List<Map<String, Object>> rows,
        long totalElements,
        int page,
        int size
) {
    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasPrevious() { return page > 0; }
    public boolean hasNext() { return page + 1 < totalPages(); }
}
