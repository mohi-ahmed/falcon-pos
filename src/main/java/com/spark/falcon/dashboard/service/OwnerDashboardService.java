package com.spark.falcon.dashboard.service;

import com.spark.falcon.analytics.dto.AnalyticsContextResponse;
import com.spark.falcon.analytics.dto.AnalyticsOverviewResponse;
import com.spark.falcon.analytics.dto.FinancialSummaryResponse;
import com.spark.falcon.analytics.service.AnalyticsService;
import com.spark.falcon.cashmanagement.dto.CashbookResponse;
import com.spark.falcon.cashmanagement.dto.CashierShiftResponse;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.dashboard.dto.*;
import com.spark.falcon.dashboard.repository.DashboardReadRepository;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.payment.dto.PaymentOverviewResponse;
import com.spark.falcon.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerDashboardService {

    private final DashboardContextService contextService;
    private final DashboardPeriodService periodService;
    private final DashboardReadRepository readRepository;
    private final AnalyticsService analyticsService;
    private final PaymentService paymentService;
    private final CashManagementService cashManagementService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OwnerDashboardResponse load(OwnerPrincipal principal, String periodValue, String monthValue) {
        DashboardContextResponse context = contextService.resolve(principal);
        ZoneId zone = zone(context.activeBranch().timeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        DashboardDateRangeResponse range = periodService.resolve(periodValue, monthValue, today);
        DashboardAccessResponse access = access(principal);

        Instant from = range.from().atStartOfDay(zone).toInstant();
        Instant toExclusive = range.to().plusDays(1).atStartOfDay(zone).toInstant();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant();
        Instant yesterdayEnd = todayStart;
        Long businessId = context.setup().businessId();
        Long branchId = context.setup().branchId();

        AnalyticsOverviewResponse analytics = access.analytics()
                ? analyticsService.overview(new AnalyticsContextResponse(context.setup(), context.activeBranch(),
                        context.accessibleBranches(), List.of(branchId)), range.from(), range.to(), range.selectedMonth())
                : null;
        FinancialSummaryResponse financial = analytics == null ? null : analytics.financial();

        DashboardSalesSummaryResponse sales = access.sales()
                ? readRepository.salesSummary(businessId, branchId, from, toExclusive, todayStart, todayEnd,
                        yesterdayStart, yesterdayEnd, financial == null ? null : financial.netSales())
                : null;
        DashboardInventorySummaryResponse inventory = access.inventory()
                ? readRepository.inventorySummary(businessId, branchId, today, java.time.YearMonth.from(today))
                : null;
        DashboardPurchaseSummaryResponse purchase = access.purchase()
                ? readRepository.purchaseSummary(businessId, branchId, from, toExclusive, todayStart, todayEnd)
                : null;
        DashboardPaymentSummaryResponse payment = access.payment()
                ? paymentSummary(paymentService.overview(principal.ownerId(), branchId, from, toExclusive))
                : null;
        DashboardCustomerSummaryResponse customer = access.customer()
                ? readRepository.customerSummary(businessId, branchId, today, zone,
                        payment == null ? null : payment.customerDueCollected())
                : null;
        DashboardCashSummaryResponse cash = access.cash()
                ? cashSummary(businessId, branchId)
                : null;
        List<DashboardChartPointResponse> chart = access.sales() || access.expense()
                ? maskChart(readRepository.chart(businessId, branchId, range.from(), range.to(), zone), access)
                : List.of();

        List<DashboardAlertResponse> alerts = alerts(principal, access, inventory, purchase, payment, customer, cash, analytics);
        List<DashboardQuickAccessResponse> quickAccess = quickAccess(principal);
        return new OwnerDashboardResponse(context, range, access, financial, sales, inventory, purchase, payment, customer, cash,
                chart,
                analytics == null ? List.of() : analytics.topProducts(),
                analytics == null ? List.of() : analytics.topCustomers(),
                analytics == null ? List.of() : analytics.leadingSuppliers(),
                analytics == null ? List.of() : analytics.paymentMix(),
                alerts, quickAccess, Instant.now(clock));
    }

    private DashboardAccessResponse access(OwnerPrincipal principal) {
        boolean analytics = has(principal, "ANALYTICS_REPORTS_ACCESS");
        boolean sales = analytics || hasAny(principal, "SELL_ACCESS", "POS_OPEN", "SALE_CREATE");
        boolean purchase = analytics || has(principal, "PURCHASE_ACCESS");
        boolean inventory = analytics || hasAny(principal, "INVENTORY_VIEW", "PRODUCT_ACCESS");
        boolean payment = analytics || hasAny(principal, "PAYMENT_VIEW", "PAYMENT_VIEW_CONSOLIDATED",
                "PAYMENT_RECEIVE_CUSTOMER_DUE", "PAYMENT_PAY_SUPPLIER_DUE");
        boolean expense = analytics || hasAny(principal, "EXPENDITURE_ACCESS", "EXPENSE_VIEW");
        boolean cash = analytics || has(principal, "CASH_MANAGEMENT_ACCESS");
        boolean customer = analytics || hasAny(principal, "CUSTOMER_ACCESS", "PAYMENT_RECEIVE_CUSTOMER_DUE");
        boolean supplier = analytics || hasAny(principal, "SUPPLIER_ACCESS", "PURCHASE_ACCESS", "PAYMENT_PAY_SUPPLIER_DUE");
        return new DashboardAccessResponse(analytics, sales, purchase, inventory, payment, expense, cash, customer,
                supplier, analytics);
    }

    private DashboardPaymentSummaryResponse paymentSummary(PaymentOverviewResponse value) {
        return new DashboardPaymentSummaryResponse(value.customerDueCollected(), value.supplierDuePaid(),
                value.cashReceipts(), value.cashOutflows(), value.cardCollections(), value.mobileBankingCollections(),
                value.bankCollections(), value.failedPayments(), value.reversedPayments(), value.pendingPayments());
    }

    private DashboardCashSummaryResponse cashSummary(Long businessId, Long branchId) {
        List<CashierShiftResponse> openShifts = openShifts(businessId, branchId);
        List<DashboardShiftCashResponse> expected = openShifts.stream()
                .map(shift -> new DashboardShiftCashResponse(shift.getId(), shift.getShiftCode(), shift.getRegisterId(),
                        shift.getCashierUserId(), shift.getOpeningTime(), safeExpectedCash(businessId, branchId, shift)))
                .toList();
        long unresolved = cashManagementService.findUnresolvedVarianceShifts(businessId, branchId).size();
        CashbookResponse cashbook = cashManagementService.findCashbook(businessId, branchId);
        BigDecimal balance = cashbook == null || cashbook.getCurrentBalance() == null
                ? BigDecimal.ZERO.setScale(4) : cashbook.getCurrentBalance();
        return new DashboardCashSummaryResponse(openShifts.size(), openShifts.size(), unresolved, balance, balance, expected);
    }


    private List<CashierShiftResponse> openShifts(Long businessId, Long branchId) {
        List<CashierShiftResponse> values = new ArrayList<>();
        int pageNumber = 0;
        Page<CashierShiftResponse> page;
        do {
            page = cashManagementService.searchShifts(businessId, branchId, null, null, CashierShiftStatus.OPEN,
                    null, null, null, null, null, PageRequest.of(pageNumber++, 100));
            values.addAll(page.getContent());
        } while (page.hasNext());
        return List.copyOf(values);
    }

    private List<DashboardChartPointResponse> maskChart(List<DashboardChartPointResponse> values,
                                                         DashboardAccessResponse access) {
        return values.stream().map(value -> new DashboardChartPointResponse(value.date(),
                access.sales() ? value.netSales() : null,
                access.expense() ? value.operatingExpense() : null)).toList();
    }

    private BigDecimal safeExpectedCash(Long businessId, Long branchId, CashierShiftResponse shift) {
        try {
            return cashManagementService.expectedCash(businessId, branchId, shift.getId());
        } catch (RuntimeException ignored) {
            return shift.getOpeningFloat() == null ? BigDecimal.ZERO.setScale(4) : shift.getOpeningFloat();
        }
    }

    private List<DashboardAlertResponse> alerts(OwnerPrincipal principal, DashboardAccessResponse access,
                                                 DashboardInventorySummaryResponse inventory,
                                                 DashboardPurchaseSummaryResponse purchase,
                                                 DashboardPaymentSummaryResponse payment,
                                                 DashboardCustomerSummaryResponse customer,
                                                 DashboardCashSummaryResponse cash,
                                                 AnalyticsOverviewResponse analytics) {
        List<DashboardAlertResponse> values = new ArrayList<>();
        if (access.inventory() && inventory != null) {
            addAlert(values, "LOW_STOCK", "Low stock products", inventory.lowStockProducts(), "warning", permittedUrl(principal, "INVENTORY_VIEW", "/owner/inventory"));
            addAlert(values, "OUT_OF_STOCK", "Out-of-stock products", inventory.outOfStockProducts(), "critical", permittedUrl(principal, "INVENTORY_VIEW", "/owner/inventory"));
            addAlert(values, "NEAR_EXPIRY", "Products expiring within 7 days", inventory.expiringWithin7Days(), "warning", permittedUrl(principal, "PRODUCT_ACCESS", "/owner/products/expiry-alert"));
            addAlert(values, "PENDING_TRANSFER", "Pending branch transfers", inventory.pendingBranchTransfers(), "warning", permittedUrl(principal, "INVENTORY_VIEW", "/owner/inventory/transfers"));
            addAlert(values, "TRANSFER_DISCREPANCY", "Transfer discrepancies", inventory.transferDiscrepancies(), "critical", permittedUrl(principal, "INVENTORY_VIEW", "/owner/inventory/transfers"));
            addAlert(values, "COUNT_VARIANCE", "Unresolved stock-count variances", inventory.unresolvedPhysicalCountVariances(), "warning", permittedUrl(principal, "INVENTORY_VIEW", "/owner/inventory/counts"));
            addAlert(values, "FAILED_STOCK_IMPORT", "Failed stock imports", inventory.failedStockImports(), "critical", permittedUrl(principal, "PURCHASE_ACCESS", "/owner/purchases/stock-import/history"));
            addAlert(values, "PENDING_STOCK_IMPORT", "Stock imports awaiting confirmation", inventory.stockImportsAwaitingConfirmation(), "warning", permittedUrl(principal, "PURCHASE_ACCESS", "/owner/purchases/stock-import/history"));
        }
        if (access.purchase() && purchase != null) {
            addAlert(values, "SUPPLIER_DUE", "Supplier due invoices", purchase.duePurchaseInvoices(), "warning", permittedUrl(principal, "PURCHASE_ACCESS", "/owner/purchases"));
            addAlert(values, "PURCHASE_RETURN", "Pending purchase returns", purchase.pendingPurchaseReturns(), "warning", permittedUrl(principal, "PURCHASE_ACCESS", "/owner/purchases/returns"));
        }
        if (access.payment() && payment != null) {
            addAlert(values, "FAILED_PAYMENT", "Failed payments", payment.failedPayments(), "critical", permittedUrlAny(principal, "/owner/payments/history", "PAYMENT_VIEW", "PAYMENT_VIEW_CONSOLIDATED"));
        }
        if (access.customer() && customer != null) {
            addAlert(values, "OVERDUE_CUSTOMER_DUE", "Overdue customer due invoices", customer.overdueInvoiceCount(),
                    "warning", permittedUrl(principal, "CUSTOMER_ACCESS", "/owner/customers/due-invoices?status=OVERDUE"));
        }
        if (access.cash() && cash != null) {
            addAlert(values, "CASH_VARIANCE", "Cash shortage or excess requiring review", cash.unresolvedShortageOrExcess(), "critical", permittedUrl(principal, "CASH_MANAGEMENT_ACCESS", "/owner/cash-management/shifts"));
        }
        if (analytics != null) {
            long failedLogins = analytics.loginLogs().stream().filter(log -> "LOGIN_FAILURE".equals(log.eventType())).count();
            addAlert(values, "SECURITY_EVENT", "Recent failed sign-in events", failedLogins, "warning", permittedUrl(principal, "ANALYTICS_REPORTS_ACCESS", "/owner/analytics"));
        }
        return List.copyOf(values);
    }

    private void addAlert(List<DashboardAlertResponse> values, String code, String label, long count,
                          String severity, String targetUrl) {
        if (count > 0) values.add(new DashboardAlertResponse(code, label, count, severity, targetUrl));
    }


    private String permittedUrl(OwnerPrincipal principal, String permission, String url) {
        return has(principal, permission) ? url : null;
    }

    private String permittedUrlAny(OwnerPrincipal principal, String url, String... permissions) {
        return hasAny(principal, permissions) ? url : null;
    }

    private List<DashboardQuickAccessResponse> quickAccess(OwnerPrincipal principal) {
        List<DashboardQuickAccessResponse> values = new ArrayList<>();
        addQuick(values, principal, "POS_OPEN", "Open POS", "/owner/pos");
        addQuick(values, principal, "PRODUCT_ACCESS", "Add Product", "/owner/products/new");
        addQuick(values, principal, "PURCHASE_ACCESS", "Create Purchase", "/owner/purchases/new");
        addQuick(values, principal, "SUPPLIER_ACCESS", "Add Supplier", "/owner/suppliers/new");
        addQuick(values, principal, "EXPENSE_CREATE", "Record Expense", "/owner/expenses/add");
        addQuick(values, principal, "PAYMENT_RECEIVE_CUSTOMER_DUE", "Receive Customer Due", "/owner/payments/customer");
        addQuick(values, principal, "PAYMENT_PAY_SUPPLIER_DUE", "Pay Supplier Due", "/owner/payments/supplier");
        if (has(principal, "INVENTORY_VIEW")) addQuick(values, principal, "INVENTORY_COUNT_CREATE_SUBMIT", "Start Stock Count", "/owner/inventory/counts/new");
        if (has(principal, "INVENTORY_VIEW")) addQuick(values, principal, "INVENTORY_TRANSFER_CREATE_SUBMIT", "Create Stock Transfer", "/owner/inventory/transfers/new");
        addQuick(values, principal, "PRODUCT_ACCESS", "View Expiry Alert", "/owner/products/expiry-alert");
        addQuick(values, principal, "ANALYTICS_REPORTS_ACCESS", "Open Analytics and Reports", "/owner/analytics");
        return List.copyOf(values);
    }

    private void addQuick(List<DashboardQuickAccessResponse> values, OwnerPrincipal principal,
                          String permission, String label, String url) {
        if (has(principal, permission)) values.add(new DashboardQuickAccessResponse(permission, label, url));
    }

    private boolean has(OwnerPrincipal principal, String permission) {
        return principal != null && principal.hasPermission(permission);
    }

    private boolean hasAny(OwnerPrincipal principal, String... permissions) {
        for (String permission : permissions) if (has(principal, permission)) return true;
        return false;
    }

    private ZoneId zone(String value) {
        try {
            return value == null || value.isBlank() ? ZoneId.systemDefault() : ZoneId.of(value);
        } catch (RuntimeException ignored) {
            return ZoneId.systemDefault();
        }
    }
}
