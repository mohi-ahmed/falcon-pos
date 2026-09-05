package com.spark.falcon.analytics.dto;

import java.util.List;
import java.util.Locale;

public enum AnalyticsReportType {
    CUSTOMER_OUTSTANDING_DUE("Customer Outstanding Due", "Customer & Payment"),
    CUSTOMER_AGING("Customer Aging", "Customer & Payment"),
    CUSTOMER_PAYMENT_COLLECTION("Customer Payment Collection", "Customer & Payment"),
    CUSTOMER_STATEMENT("Customer Statement", "Customer & Payment"),
    CUSTOMER_CREDIT("Customer Credit", "Customer & Payment"),
    SUPPLIER_DUE_PAYMENT("Supplier Due Payment", "Customer & Payment"),
    PAYMENT_METHOD_SUMMARY("Payment Method Summary", "Customer & Payment"),
    FAILED_PAYMENT("Failed Payment", "Customer & Payment"),
    REVERSED_PAYMENT("Reversed Payment", "Customer & Payment"),
    CASH_VS_NON_CASH_COLLECTION("Cash vs Non-Cash Collection", "Customer & Payment"),
    OPERATING_EXPENSE("Operating Expense", "Expense & Cashflow"),
    EXPENSE_CATEGORY("Expense Category", "Expense & Cashflow"),
    RECOVERABLE_DEPOSIT_ADVANCE("Recoverable Deposit & Advance", "Expense & Cashflow"),
    CASH_OUTFLOW("Cash Outflow", "Expense & Cashflow"),
    SUPPLIER_PAYMENT("Supplier Payment", "Expense & Cashflow"),
    CUSTOMER_REFUND("Customer Refund", "Expense & Cashflow"),
    INVENTORY_LOSS("Inventory Loss", "Expense & Cashflow"),
    EXPIRY("Expiry Report", "Expiry"),
    NEAR_EXPIRY_STOCK("Near-Expiry Stock", "Expiry"),
    EXPIRED_STOCK_VALUATION("Expired Stock Valuation", "Expiry"),
    EXPIRY_WASTAGE("Expiry Wastage", "Expiry"),
    SUPPLIER_WISE_EXPIRY_LOSS("Supplier-wise Expiry Loss", "Expiry"),
    BRANCH_WISE_EXPIRY("Branch-wise Expiry", "Expiry"),
    EXPIRY_PURCHASE_RETURN("Expiry Purchase Return", "Expiry");

    private final String title;
    private final String category;

    AnalyticsReportType(String title, String category) {
        this.title = title;
        this.category = category;
    }

    public String title() { return title; }
    public String category() { return category; }
    public String path() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    public static AnalyticsReportType fromPath(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Report type is required.");
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public boolean supportsDateRange() {
        return this != CUSTOMER_AGING && this != CUSTOMER_CREDIT && this != NEAR_EXPIRY_STOCK
                && this != EXPIRED_STOCK_VALUATION && this != BRANCH_WISE_EXPIRY;
    }

    public boolean supportsProduct() {
        return this == INVENTORY_LOSS || category.equals("Expiry");
    }

    public boolean supportsCategory() {
        return this == OPERATING_EXPENSE || this == EXPENSE_CATEGORY || this == INVENTORY_LOSS || category.equals("Expiry");
    }

    public boolean supportsSupplier() {
        return this == SUPPLIER_DUE_PAYMENT || this == SUPPLIER_PAYMENT || category.equals("Expiry");
    }

    public boolean supportsCustomer() {
        return this == CUSTOMER_OUTSTANDING_DUE || this == CUSTOMER_AGING || this == CUSTOMER_PAYMENT_COLLECTION
                || this == CUSTOMER_STATEMENT || this == CUSTOMER_CREDIT || this == CUSTOMER_REFUND;
    }

    public boolean supportsPaymentMethod() {
        return this == CUSTOMER_PAYMENT_COLLECTION || this == SUPPLIER_DUE_PAYMENT || this == SUPPLIER_PAYMENT
                || this == PAYMENT_METHOD_SUMMARY || this == OPERATING_EXPENSE;
    }

    public boolean supportsBatch() { return category.equals("Expiry"); }
    public boolean supportsExpiryStatus() { return this == EXPIRY || this == NEAR_EXPIRY_STOCK; }

    public boolean supportsChronologicalSort() {
        return switch (this) {
            case CUSTOMER_AGING, PAYMENT_METHOD_SUMMARY, CASH_VS_NON_CASH_COLLECTION, EXPENSE_CATEGORY,
                 SUPPLIER_WISE_EXPIRY_LOSS, BRANCH_WISE_EXPIRY -> false;
            default -> true;
        };
    }

    public boolean supportsSearch() {
        return switch (this) {
            case PAYMENT_METHOD_SUMMARY, CASH_VS_NON_CASH_COLLECTION, EXPENSE_CATEGORY, CUSTOMER_CREDIT,
                 SUPPLIER_WISE_EXPIRY_LOSS, BRANCH_WISE_EXPIRY -> false;
            default -> true;
        };
    }

    public static List<AnalyticsReportType> customerPaymentReports() {
        return List.of(CUSTOMER_OUTSTANDING_DUE, CUSTOMER_AGING, CUSTOMER_PAYMENT_COLLECTION,
                CUSTOMER_STATEMENT, CUSTOMER_CREDIT, SUPPLIER_DUE_PAYMENT, PAYMENT_METHOD_SUMMARY,
                FAILED_PAYMENT, REVERSED_PAYMENT, CASH_VS_NON_CASH_COLLECTION);
    }

    public static List<AnalyticsReportType> expenseCashflowReports() {
        return List.of(OPERATING_EXPENSE, EXPENSE_CATEGORY, RECOVERABLE_DEPOSIT_ADVANCE,
                CASH_OUTFLOW, SUPPLIER_PAYMENT, CUSTOMER_REFUND, INVENTORY_LOSS);
    }

    public static List<AnalyticsReportType> expiryReports() {
        return List.of(EXPIRY, NEAR_EXPIRY_STOCK, EXPIRED_STOCK_VALUATION, EXPIRY_WASTAGE,
                SUPPLIER_WISE_EXPIRY_LOSS, BRANCH_WISE_EXPIRY, EXPIRY_PURCHASE_RETURN);
    }
}
