package com.spark.falcon.analytics.service;

import com.spark.falcon.analytics.dto.*;
import com.spark.falcon.analytics.repository.AnalyticsReadRepository;
import com.spark.falcon.shared.export.ExportDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsReportService {

    private static final int EXPORT_CHUNK_SIZE = 500;

    private final AnalyticsReadRepository repository;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public ReportPageResponse page(AnalyticsReportType type,
                                   AnalyticsContextResponse context,
                                   ReportFilter filter) {
        ZoneId zone = analyticsService.zone(context);
        analyticsService.validateRange(filter.from(), filter.to(), 1826);
        Instant from = filter.from().atStartOfDay(zone).toInstant();
        Instant toExclusive = filter.to().plusDays(1).atStartOfDay(zone).toInstant();
        LocalDate today = LocalDate.now(zone);
        return repository.reportPage(type, context.setup().businessId(), context.reportBranchIds(),
                filter, today, from, toExclusive, zone);
    }

    public ExportDocument exportDocument(AnalyticsReportType type,
                                         AnalyticsContextResponse context,
                                         ReportFilter filter) {
        ZoneId zone = analyticsService.zone(context);
        analyticsService.validateRange(filter.from(), filter.to(), 1826);
        Instant from = filter.from().atStartOfDay(zone).toInstant();
        Instant toExclusive = filter.to().plusDays(1).atStartOfDay(zone).toInstant();
        LocalDate today = LocalDate.now(zone);
        List<ReportColumnResponse> reportColumns = repository.columns(type);
        Map<String, String> metadata = filterMetadata(type, context, filter);

        return new ExportDocument(type.title(), context.setup().businessName(), scopeName(context), zone,
                metadata, reportColumns.stream().map(ReportColumnResponse::exportColumn).toList(), consumer -> {
            int offset = 0;
            while (true) {
                List<Map<String, Object>> rows = repository.reportChunk(type, context.setup().businessId(),
                        context.reportBranchIds(), filter, today, from, toExclusive, zone, offset, EXPORT_CHUNK_SIZE);
                if (rows.isEmpty()) break;
                for (Map<String, Object> row : rows) {
                    Object[] values = reportColumns.stream().map(column -> row.get(column.key())).toArray();
                    consumer.accept(values);
                }
                if (rows.size() < EXPORT_CHUNK_SIZE) break;
                offset += rows.size();
            }
        });
    }

    @Transactional(readOnly = true)
    public ReportFilterOptionsResponse filterOptions(AnalyticsContextResponse context) {
        return repository.filterOptions(context.setup().businessId(), context.reportBranchIds());
    }

    public List<AnalyticsReportType> customerPaymentReports() { return AnalyticsReportType.customerPaymentReports(); }
    public List<AnalyticsReportType> expenseCashflowReports() { return AnalyticsReportType.expenseCashflowReports(); }
    public List<AnalyticsReportType> expiryReports() { return AnalyticsReportType.expiryReports(); }

    private String scopeName(AnalyticsContextResponse context) {
        return context.reportBranchIds().size() == 1 ? context.reportBranch().branchName() : "All accessible branches";
    }

    private Map<String, String> filterMetadata(AnalyticsReportType type, AnalyticsContextResponse context, ReportFilter filter) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Branch", scopeName(context));
        if (type.supportsDateRange()) {
            values.put("From", filter.from().toString());
            values.put("To", filter.to().toString());
        } else {
            values.put("Position", "Current");
        }
        if (filter.query() != null) values.put("Search", filter.query());
        if (filter.batchNumber() != null) values.put("Batch", filter.batchNumber());
        if (filter.expiryStatus() != null) values.put("Expiry Status", filter.expiryStatus());
        return values;
    }
}
