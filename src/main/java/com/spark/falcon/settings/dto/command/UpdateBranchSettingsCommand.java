package com.spark.falcon.settings.dto.command;

import java.math.BigDecimal;
import com.spark.falcon.settings.entity.enumtype.OpeningFloatPolicy;

public record UpdateBranchSettingsCommand(
        Long ownerId, Long branchId,
        String name, String code, String country, String email, String phone, String timeZone,
        String address, String city, String stateDivision, String postalCode, String vatBinNumber,
        BigDecimal defaultTaxRate, Integer lowStockAlertQuantity, Integer recordsPerPage, String receiptFooter,
        String defaultCashierName, Integer draftHeldEditWindowMinutes, String afterSalePage,
        Boolean autoPrintReceipt, Long receiptPrinterId, Boolean posSoundEffectsEnabled, Integer defaultExpiryAlertDays,
        Boolean fefoEnabled, Boolean blockExpiredSales, Boolean nearExpiryWarningsEnabled,
        Boolean allowMissingExpiryInformation,
        String defaultDepositAccountReference, Boolean openShiftRequiredForCashTransactions,
        Integer maximumConcurrentShiftsPerRegister, Boolean cashierMultipleShiftsAllowed,
        OpeningFloatPolicy openingFloatPolicy, BigDecimal fixedOpeningFloatAmount,
        String allowedOpeningSources, String allowedClosingDestinations, Boolean cashDropRequired,
        Boolean handoverRequired, Boolean denominationCountRequired, BigDecimal varianceApprovalThreshold,
        BigDecimal highValueTransferApprovalThreshold, Boolean segregationOfDutiesRequired,
        String smsGateway, Boolean smsNotificationsEnabled, Boolean automaticInvoiceSmsEnabled,
        Integer generalExpirationReminderDays, String expirationNotificationRecipients,
        String businessLogoReference, String faviconReference, Boolean scheduledJobsEnabled) {
}
