package com.spark.falcon.user.entity.enumtype;

public enum SystemPermissionCode {
    USER_MANAGEMENT("Manage users and user groups"),
    BRANCH_MANAGEMENT("Manage branches"),
    BRANCH_CHANGE("Change active branch"),
    SETTINGS_ACCESS("Access settings"),
    POS_OPEN("Open POS"),
    SALE_CREATE("Create sale"),
    SELL_ACCESS("Access Sales"),
    PURCHASE_ACCESS("Access Purchases"),
    SUPPLIER_ACCESS("Access Suppliers"),
    CUSTOMER_ACCESS("Access Customers"),
    PRODUCT_ACCESS("Access Product"),
    PRODUCT_EXPIRY_DISABLE("Disable Product Expiry Tracking"),
    EXPENDITURE_ACCESS("Access Expenditure"),
    CASH_MANAGEMENT_ACCESS("Access Cash Management"),
    ANALYTICS_REPORTS_ACCESS("Access Analytics and Reports"),
    INVENTORY_ADJUSTMENT("Inventory Adjustment"),
    PAYMENT_VIEW("View Payment"),
    PAYMENT_VIEW_REFERENCE("View Payment Reference"),
    PAYMENT_RECEIVE_CUSTOMER_DUE("Receive Customer Due"),
    PAYMENT_PAY_SUPPLIER_DUE("Pay Supplier Due"),
    PAYMENT_CONFIRM("Confirm Payment"),
    PAYMENT_REVERSE("Reverse Payment"),
    PAYMENT_APPROVE_HIGH_VALUE("Approve High-Value Payment"),
    PAYMENT_EXPORT("Export Payment"),
    PAYMENT_VIEW_CONSOLIDATED("View Consolidated Payments"),
    EXPENSE_VIEW("View Expense"), EXPENSE_CREATE("Create Expense"), EXPENSE_SUBMIT("Submit Expense"),
    EXPENSE_APPROVE("Approve Expense"), EXPENSE_POST("Post Expense"), EXPENSE_REVERSE("Reverse Expense"),
    EXPENSE_EXPORT("Export Expense"), EXPENSE_VIEW_CONSOLIDATED("View Consolidated Expense"),
    INVENTORY_VIEW("View Inventory"), INVENTORY_COST_VIEW("View Inventory Cost"), INVENTORY_EXPORT("Export Inventory"),
    INVENTORY_COUNT_CREATE_SUBMIT("Create and Submit Stock Count"), INVENTORY_COUNT_REVIEW_APPROVE("Review and Approve Stock Count"),
    INVENTORY_COUNT_POST_VARIANCE("Post Stock Count Variance"), INVENTORY_ADJUSTMENT_CREATE_SUBMIT("Create and Submit Adjustment"),
    INVENTORY_ADJUSTMENT_APPROVE_POST("Approve and Post Adjustment"), INVENTORY_ADJUSTMENT_REVERSE("Reverse Adjustment"),
    INVENTORY_TRANSFER_CREATE_SUBMIT("Create and Submit Transfer"), INVENTORY_TRANSFER_APPROVE("Approve Transfer"),
    INVENTORY_TRANSFER_DISPATCH("Dispatch Transfer"), INVENTORY_TRANSFER_RECEIVE("Receive Transfer"),
    INVENTORY_TRANSFER_RESOLVE_DISCREPANCY("Resolve Transfer Discrepancy"),
    INVENTORY_WASTAGE_CREATE_SUBMIT("Create and Submit Wastage"), INVENTORY_WASTAGE_APPROVE_POST("Approve and Post Wastage"),
    INVENTORY_WASTAGE_REVERSE("Reverse Wastage");

    private final String displayName;

    SystemPermissionCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
