package com.spark.falcon.settings.dto.request;

import com.spark.falcon.settings.entity.enumtype.OpeningFloatPolicy;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class BranchSettingsRequest {
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]+", message = "Use letters, numbers and hyphens only")
    private String code;
    @NotBlank @Size(min = 2, max = 2) private String country;
    @NotBlank @Email @Size(max = 160) private String email;
    @NotBlank @Size(max = 32) private String phone;
    @NotBlank @Size(max = 64) private String timeZone;
    @NotBlank @Size(max = 240) private String address;
    @NotBlank @Size(max = 80) private String city;
    @Size(max = 80) private String stateDivision;
    @Size(max = 16) private String postalCode;
    @Size(max = 40) private String vatBinNumber;
    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
    private BigDecimal defaultTaxRate;
    @NotNull @Min(0) @Max(999999) private Integer lowStockAlertQuantity;
    @NotNull private Integer recordsPerPage;
    @Size(max = 240) private String receiptFooter;
    @Size(max = 120) private String defaultCashierName;
    @Min(0) private Integer draftHeldEditWindowMinutes;
    @Size(max = 120) private String afterSalePage;
    private Boolean autoPrintReceipt;
    private Long receiptPrinterId;
    private Boolean posSoundEffectsEnabled;
    @Min(1) private Integer defaultExpiryAlertDays;
    private Boolean fefoEnabled;
    private Boolean blockExpiredSales = Boolean.TRUE;
    private Boolean nearExpiryWarningsEnabled;
    private Boolean allowMissingExpiryInformation;
    @Size(max = 120) private String defaultDepositAccountReference;
    private Boolean openShiftRequiredForCashTransactions;
    @Min(1) @Max(100) private Integer maximumConcurrentShiftsPerRegister;
    private Boolean cashierMultipleShiftsAllowed;
    private OpeningFloatPolicy openingFloatPolicy;
    @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) private BigDecimal fixedOpeningFloatAmount;
    @Size(max = 240) private String allowedOpeningSources;
    @Size(max = 240) private String allowedClosingDestinations;
    private Boolean cashDropRequired;
    private Boolean handoverRequired;
    private Boolean denominationCountRequired;
    @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) private BigDecimal varianceApprovalThreshold;
    @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) private BigDecimal highValueTransferApprovalThreshold;
    private Boolean segregationOfDutiesRequired;
    @Size(max = 120) private String smsGateway;
    private Boolean smsNotificationsEnabled;
    private Boolean automaticInvoiceSmsEnabled;
    @Min(1) @Max(3650) private Integer generalExpirationReminderDays;
    @Size(max = 240) private String expirationNotificationRecipients;
    @Size(max = 500) private String businessLogoReference;
    @Size(max = 500) private String faviconReference;
    private Boolean scheduledJobsEnabled;
}
