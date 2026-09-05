package com.spark.falcon.cashmanagement.controller;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.cashmanagement.entity.CashMovementStatus;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import com.spark.falcon.cashmanagement.entity.CashVarianceResult;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.shared.export.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CashManagementExportController {
    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final CashManagementService cashManagementService;
    private final ExportResponse exportResponse;

    @GetMapping("/owner/cash-management/shifts/export")
    public void shifts(@RequestParam(required = false) Long registerId,
                       @RequestParam(required = false) Long cashierId,
                       @RequestParam(required = false) CashierShiftStatus status,
                       @RequestParam(required = false) CashVarianceResult varianceResult,
                       @RequestParam(required = false) Boolean approvalRequired,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String q,
                       @RequestParam String format,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        ZoneId zone = activeBranchZone(setup.businessId(), setup.branchId());
        if (from != null && to != null && from.isAfter(to)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "From date cannot be later than To date.");
            return;
        }
        var page = cashManagementService.searchShifts(
                setup.businessId(), setup.branchId(), registerId, cashierId, status, varianceResult, approvalRequired,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(), q,
                Pageable.unpaged(Sort.by(Sort.Direction.DESC, "openingTime").and(Sort.by(Sort.Direction.DESC, "id"))));
        var rows = page.getContent();
        var summaries = cashManagementService.summarizeShifts(setup.businessId(), setup.branchId(),
                rows.stream().map(v -> v.getId()).toList());

        write(format, "Cashier Shift List and History", setup.businessName(), setup.branchName(), zone, List.of(
                ExportColumn.text("Shift ID"), ExportColumn.text("Branch"), ExportColumn.number("Register ID"),
                ExportColumn.number("Cashier ID"), ExportColumn.dateTime("Opened"), ExportColumn.dateTime("Closed"),
                ExportColumn.money("Opening Float"), ExportColumn.money("Cash Inflows"), ExportColumn.money("Cash Outflows"),
                ExportColumn.money("Transfers In"), ExportColumn.money("Transfers Out"), ExportColumn.money("Expected Cash"),
                ExportColumn.money("Counted Cash"), ExportColumn.money("Variance"), ExportColumn.text("Result"),
                ExportColumn.text("Status"), ExportColumn.text("Approver")), c -> {
            for (var v : rows) {
                var summary = summaries.getOrDefault(v.getId(), com.spark.falcon.cashmanagement.dto.CashierShiftMovementSummaryResponse.empty());
                c.accept(v.getShiftCode(), setup.branchName(), v.getRegisterId(), v.getCashierUserId(),
                        v.getOpeningTime(), v.getClosingTime(), v.getOpeningFloat(), summary.cashInflows(), summary.cashOutflows(),
                        summary.transfersIn(), summary.transfersOut(), v.getExpectedCash(), v.getPhysicalCountedCash(),
                        v.getCashVariance(), v.getVarianceResult(), v.getStatus(),
                        v.isVarianceApprovalRequired() ? "Owner #" + v.getVarianceReviewedByOwnerId() : "Not required");
            }
        }, response);
    }

    @GetMapping("/owner/cash-management/export")
    public void cashbook(@RequestParam(required = false) Long cashLocationId,
                         @RequestParam(required = false) Long registerId,
                         @RequestParam(required = false) Long shiftId,
                         @RequestParam(required = false) CashMovementType movementType,
                         @RequestParam(required = false) CashSourceModule sourceModule,
                         @RequestParam(required = false) Long userId,
                         @RequestParam(required = false) CashMovementStatus status,
                         @RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to,
                         @RequestParam(required = false) String q,
                         @RequestParam String format,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         HttpServletResponse response) throws IOException {
        var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        ZoneId zone = activeBranchZone(setup.businessId(), setup.branchId());
        if (from != null && to != null && from.isAfter(to)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "From date cannot be later than To date.");
            return;
        }
        var page = cashManagementService.searchMovements(
                setup.businessId(), setup.branchId(), cashLocationId, registerId, shiftId, movementType, sourceModule,
                userId, status, from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(), q,
                Pageable.unpaged(Sort.by(Sort.Direction.DESC, "postedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        var rows = page.getContent();
        write(format, "Cashbook Movement List", setup.businessName(), setup.branchName(), zone, List.of(
                ExportColumn.number("Movement ID"), ExportColumn.dateTime("Posted At"), ExportColumn.text("Type"),
                ExportColumn.text("Direction"), ExportColumn.money("Amount"), ExportColumn.money("Balance Before"),
                ExportColumn.money("Balance After"), ExportColumn.text("Source"), ExportColumn.text("Reference"),
                ExportColumn.number("Cash Location ID"), ExportColumn.number("Register ID"), ExportColumn.number("Shift ID"),
                ExportColumn.number("User ID"), ExportColumn.text("Status")), c -> {
            for (var v : rows) {
                c.accept(v.getId(), v.getPostedAt(), v.getMovementType(), v.getDirection(), v.getAmount(),
                        v.getBalanceBefore(), v.getBalanceAfter(), v.getSourceModule(), v.getSourceReference(),
                        v.getCashLocationId(), v.getRegisterId(), v.getCashierShiftId(), v.getPostedByUserId(), v.getStatus());
            }
        }, response);
    }

    private ZoneId activeBranchZone(Long businessId, Long branchId) {
        var branch = branchAccessService.findByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(CashManagementAccessDeniedException::new);
        return ZoneId.of(branch.timeZone());
    }

    private void write(String format, String title, String business, String branch, ZoneId zone,
                       List<ExportColumn> columns, ExportRowSource rows, HttpServletResponse response) throws IOException {
        exportResponse.write(format, new ExportDocument(title, business, branch, zone,
                Map.of("Time Zone", zone.getId()), columns, rows), response);
    }
}
