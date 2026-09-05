package com.spark.falcon.analytics.controller;

import com.spark.falcon.analytics.dto.*;
import com.spark.falcon.analytics.exception.AnalyticsValidationException;
import com.spark.falcon.analytics.service.AnalyticsAccessService;
import com.spark.falcon.analytics.service.AnalyticsReportService;
import com.spark.falcon.analytics.service.AnalyticsService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.shared.export.ExportResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
@RequestMapping("/owner/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsAccessService accessService;
    private final AnalyticsService analyticsService;
    private final AnalyticsReportService reportService;
    private final ExportResponse exportResponse;

    @GetMapping
    public String overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        AnalyticsContextResponse context = accessService.overviewContext(principal);
        AnalyticsOverviewResponse overview;
        try {
            overview = analyticsService.overview(context, from, to, month);
        } catch (AnalyticsValidationException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            overview = analyticsService.overview(context, null, null, month);
        }
        common(model, context, "analytics-overview");
        model.addAttribute("overview", overview);
        model.addAttribute("chartMonth", month == null ? YearMonth.from(overview.to()) : month);
        reportCatalog(model);
        return "analytics/overview";
    }

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        AnalyticsContextResponse context = accessService.overviewContext(principal);
        common(model, context, "analytics-reports");
        reportCatalog(model);
        return "analytics/reports";
    }

    @GetMapping("/reports/{reportKey}")
    public String report(@PathVariable String reportKey,
                         @RequestParam(required = false) Long branchId,
                         @RequestParam(defaultValue = "false") boolean allBranches,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) Long productId,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) Long supplierId,
                         @RequestParam(required = false) Long customerId,
                         @RequestParam(required = false) Long paymentMethodId,
                         @RequestParam(required = false) String batchNumber,
                         @RequestParam(required = false) String expiryStatus,
                         @RequestParam(required = false, name = "q") String query,
                         @RequestParam(defaultValue = "date_desc") String sort,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "25") int size,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model) {
        AnalyticsReportType type = type(reportKey);
        AnalyticsContextResponse context = accessService.reportContext(principal, type, branchId, allBranches);
        LocalDate today = LocalDate.now(analyticsService.zone(context));
        LocalDate effectiveTo = to == null ? today : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
        String effectiveSort = !type.supportsChronologicalSort() && "date_desc".equalsIgnoreCase(sort)
                ? "name_asc" : sort;
        ReportFilter filter = new ReportFilter(effectiveFrom, effectiveTo, productId, categoryId, supplierId,
                customerId, paymentMethodId, batchNumber, expiryStatus, query, effectiveSort, page, size, allBranches);
        ReportPageResponse reportPage;
        try {
            reportPage = reportService.page(type, context, filter);
        } catch (AnalyticsValidationException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            filter = new ReportFilter(today.withDayOfMonth(1), today, productId, categoryId, supplierId,
                    customerId, paymentMethodId, batchNumber, null, query, "date_desc", 0, 25, allBranches);
            reportPage = reportService.page(type, context, filter);
        }

        common(model, context, "analytics-reports");
        reportCatalog(model);
        model.addAttribute("reportType", type);
        model.addAttribute("reportPage", reportPage);
        model.addAttribute("filter", filter);
        model.addAttribute("reportScopeName", context.reportBranchIds().size() == 1
                ? context.reportBranch().branchName() : "All accessible branches");
        model.addAttribute("filterOptions", reportService.filterOptions(context));
        model.addAttribute("allowAllBranches", type == AnalyticsReportType.BRANCH_WISE_EXPIRY
                || (type == AnalyticsReportType.CUSTOMER_STATEMENT && principal.isOwner()));
        return "analytics/report-details";
    }

    @GetMapping("/reports/{reportKey}/export")
    public void export(@PathVariable String reportKey,
                       @RequestParam String format,
                       @RequestParam(required = false) Long branchId,
                       @RequestParam(defaultValue = "false") boolean allBranches,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long productId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long supplierId,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) Long paymentMethodId,
                       @RequestParam(required = false) String batchNumber,
                       @RequestParam(required = false) String expiryStatus,
                       @RequestParam(required = false, name = "q") String query,
                       @RequestParam(defaultValue = "date_desc") String sort,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       HttpServletResponse response) throws IOException {
        AnalyticsReportType type = type(reportKey);
        AnalyticsContextResponse context = accessService.reportContext(principal, type, branchId, allBranches);
        ReportFilter filter = new ReportFilter(from, to, productId, categoryId, supplierId, customerId,
                paymentMethodId, batchNumber, expiryStatus, query, sort, 0, 100, allBranches);
        exportResponse.write(format, reportService.exportDocument(type, context, filter), response);
    }

    private AnalyticsReportType type(String reportKey) {
        try { return AnalyticsReportType.fromPath(reportKey); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analytics report not found.");
        }
    }

    private void common(Model model, AnalyticsContextResponse context, String activePage) {
        model.addAttribute("setup", context.setup());
        model.addAttribute("activeSection", "analytics");
        model.addAttribute("activePage", activePage);
        model.addAttribute("reportBranch", context.reportBranch());
        model.addAttribute("accessibleBranches", context.accessibleBranches());
        model.addAttribute("branchZone", analyticsService.zone(context));
        model.addAttribute("currency", context.reportBranch().currency());
    }

    private void reportCatalog(Model model) {
        model.addAttribute("customerPaymentReports", reportService.customerPaymentReports());
        model.addAttribute("expenseCashflowReports", reportService.expenseCashflowReports());
        model.addAttribute("expiryReports", reportService.expiryReports());
    }
}
