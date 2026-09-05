package com.spark.falcon.settings.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.businesssetup.entity.Business;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import com.spark.falcon.settings.entity.enumtype.OpeningFloatPolicy;
import java.util.Objects;

@Entity
@Table(name = "branch_settings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_settings_branch", columnNames = "branch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class BranchSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_settings_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_settings_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "default_cashier_name", length = 120)
    private String defaultCashierName;

    @Column(name = "draft_held_edit_window_minutes")
    private Integer draftHeldEditWindowMinutes;

    @Column(name = "after_sale_page", length = 120)
    private String afterSalePage;

    @Column(name = "auto_print_receipt")
    private Boolean autoPrintReceipt;

    @Column(name = "receipt_printer_id")
    private Long receiptPrinterId;

    @Column(name = "pos_sound_effects_enabled")
    private Boolean posSoundEffectsEnabled;

    @Column(name = "default_expiry_alert_days")
    private Integer defaultExpiryAlertDays;

    @Column(name = "fefo_enabled")
    private Boolean fefoEnabled;

    @Column(name = "block_expired_sales", nullable = false)
    private boolean blockExpiredSales;

    @Column(name = "near_expiry_warnings_enabled")
    private Boolean nearExpiryWarningsEnabled;

    @Column(name = "allow_missing_expiry_information")
    private Boolean allowMissingExpiryInformation;

    @Column(name = "default_deposit_account_reference", length = 120) private String defaultDepositAccountReference;
    @Column(name = "open_shift_required_for_cash", nullable = false, columnDefinition = "boolean default true")
    private boolean openShiftRequiredForCashTransactions;
    @Column(name = "maximum_concurrent_shifts_per_register") private Integer maximumConcurrentShiftsPerRegister;
    @Column(name = "cashier_multiple_shifts_allowed", nullable = false, columnDefinition = "boolean default false")
    private boolean cashierMultipleShiftsAllowed;
    @Enumerated(EnumType.STRING) @Column(name = "opening_float_policy", length = 30) private OpeningFloatPolicy openingFloatPolicy;
    @Column(name = "fixed_opening_float_amount", precision = 17, scale = 2) private BigDecimal fixedOpeningFloatAmount;
    @Column(name = "allowed_opening_sources", length = 240) private String allowedOpeningSources;
    @Column(name = "allowed_closing_destinations", length = 240) private String allowedClosingDestinations;
    @Column(name = "cash_drop_required", nullable = false, columnDefinition = "boolean default false")
    private boolean cashDropRequired;
    @Column(name = "handover_required", nullable = false, columnDefinition = "boolean default false")
    private boolean handoverRequired;
    @Column(name = "denomination_count_required", nullable = false, columnDefinition = "boolean default false")
    private boolean denominationCountRequired;
    @Column(name = "variance_approval_threshold", precision = 17, scale = 2) private BigDecimal varianceApprovalThreshold;
    @Column(name = "high_value_transfer_approval_threshold", precision = 17, scale = 2) private BigDecimal highValueTransferApprovalThreshold;
    @Column(name = "segregation_of_duties_required", nullable = false, columnDefinition = "boolean default false")
    private boolean segregationOfDutiesRequired;
    @Column(name = "sms_gateway", length = 120) private String smsGateway;
    @Column(name = "sms_notifications_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean smsNotificationsEnabled;
    @Column(name = "automatic_invoice_sms_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean automaticInvoiceSmsEnabled;
    @Column(name = "general_expiration_reminder_days") private Integer generalExpirationReminderDays;
    @Column(name = "expiration_notification_recipients", length = 240) private String expirationNotificationRecipients;
    @Column(name = "business_logo_reference", length = 500) private String businessLogoReference;
    @Column(name = "favicon_reference", length = 500) private String faviconReference;
    @Column(name = "scheduled_jobs_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean scheduledJobsEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static BranchSettings create(Long businessId, Long branchId, Instant now) {
        BranchSettings settings = new BranchSettings();
        settings.businessId = Objects.requireNonNull(businessId, "businessId is required");
        settings.branchId = Objects.requireNonNull(branchId, "branchId is required");
        settings.fefoEnabled = true;
        settings.blockExpiredSales = true;
        settings.openShiftRequiredForCashTransactions = true;
        settings.maximumConcurrentShiftsPerRegister = 1;
        settings.openingFloatPolicy = OpeningFloatPolicy.OPTIONAL;
        settings.handoverRequired = true;
        settings.createdAt = Objects.requireNonNull(now, "now is required");
        settings.updatedAt = now;
        return settings;
    }

    public void update(String defaultCashierName, Integer draftHeldEditWindowMinutes, String afterSalePage,
                       Boolean autoPrintReceipt, Long receiptPrinterId, Boolean posSoundEffectsEnabled, Integer defaultExpiryAlertDays,
                       Boolean fefoEnabled, Boolean blockExpiredSales, Boolean nearExpiryWarningsEnabled,
                       Boolean allowMissingExpiryInformation, Instant now) {
        this.defaultCashierName = optional(defaultCashierName);
        this.draftHeldEditWindowMinutes = draftHeldEditWindowMinutes;
        this.afterSalePage = optional(afterSalePage);
        this.autoPrintReceipt = autoPrintReceipt;
        this.receiptPrinterId = receiptPrinterId;
        this.posSoundEffectsEnabled = posSoundEffectsEnabled;
        this.defaultExpiryAlertDays = defaultExpiryAlertDays;
        this.fefoEnabled = fefoEnabled;
        this.blockExpiredSales = blockExpiredSales == null || blockExpiredSales;
        this.nearExpiryWarningsEnabled = nearExpiryWarningsEnabled;
        this.allowMissingExpiryInformation = allowMissingExpiryInformation;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void updateDocumentedConfiguration(String defaultDepositAccountReference,
            Boolean openShiftRequiredForCashTransactions, Integer maximumConcurrentShiftsPerRegister,
            Boolean cashierMultipleShiftsAllowed, OpeningFloatPolicy openingFloatPolicy,
            BigDecimal fixedOpeningFloatAmount, String allowedOpeningSources, String allowedClosingDestinations,
            Boolean cashDropRequired, Boolean handoverRequired, Boolean denominationCountRequired,
            BigDecimal varianceApprovalThreshold, BigDecimal highValueTransferApprovalThreshold,
            Boolean segregationOfDutiesRequired, String smsGateway, Boolean smsNotificationsEnabled,
            Boolean automaticInvoiceSmsEnabled, Integer generalExpirationReminderDays,
            String expirationNotificationRecipients, String businessLogoReference, String faviconReference,
            Boolean scheduledJobsEnabled, Instant now) {
        this.defaultDepositAccountReference = optional(defaultDepositAccountReference);
        this.openShiftRequiredForCashTransactions = !Boolean.FALSE.equals(openShiftRequiredForCashTransactions);
        this.maximumConcurrentShiftsPerRegister = maximumConcurrentShiftsPerRegister == null
                ? 1 : maximumConcurrentShiftsPerRegister;
        this.cashierMultipleShiftsAllowed = Boolean.TRUE.equals(cashierMultipleShiftsAllowed);
        this.openingFloatPolicy = openingFloatPolicy == null ? OpeningFloatPolicy.OPTIONAL : openingFloatPolicy;
        this.fixedOpeningFloatAmount = fixedOpeningFloatAmount;
        this.allowedOpeningSources = optional(allowedOpeningSources);
        this.allowedClosingDestinations = optional(allowedClosingDestinations);
        this.cashDropRequired = Boolean.TRUE.equals(cashDropRequired);
        this.handoverRequired = true;
        this.denominationCountRequired = Boolean.TRUE.equals(denominationCountRequired);
        this.varianceApprovalThreshold = varianceApprovalThreshold;
        this.highValueTransferApprovalThreshold = highValueTransferApprovalThreshold;
        this.segregationOfDutiesRequired = Boolean.TRUE.equals(segregationOfDutiesRequired);
        this.smsGateway = optional(smsGateway);
        this.smsNotificationsEnabled = Boolean.TRUE.equals(smsNotificationsEnabled);
        this.automaticInvoiceSmsEnabled = Boolean.TRUE.equals(automaticInvoiceSmsEnabled);
        this.generalExpirationReminderDays = generalExpirationReminderDays;
        this.expirationNotificationRecipients = optional(expirationNotificationRecipients);
        this.businessLogoReference = optional(businessLogoReference);
        this.faviconReference = optional(faviconReference);
        this.scheduledJobsEnabled = Boolean.TRUE.equals(scheduledJobsEnabled);
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
