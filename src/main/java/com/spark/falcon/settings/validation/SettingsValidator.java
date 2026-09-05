package com.spark.falcon.settings.validation;

import com.spark.falcon.settings.dto.command.UpdateBranchSettingsCommand;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Component
public class SettingsValidator {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final Set<String> AFTER_SALE_PAGES = Set.of("POS", "INVOICE", "SELL_LIST");

    public void validateBranchSettings(UpdateBranchSettingsCommand command) {
        if (command.recordsPerPage() == null || !PAGE_SIZES.contains(command.recordsPerPage()))
            throw new IllegalArgumentException("recordsPerPage must be one of 10, 25, 50 or 100");
        if (command.country() == null || command.country().trim().length() != 2)
            throw new IllegalArgumentException("country must use a two-letter code");
        if (command.timeZone() == null || command.timeZone().isBlank())
            throw new IllegalArgumentException("timeZone is required");
        ZoneId.of(command.timeZone());
        if (command.draftHeldEditWindowMinutes() != null && command.draftHeldEditWindowMinutes() < 0)
            throw new IllegalArgumentException("draftHeldEditWindowMinutes must not be negative");
        if (command.defaultExpiryAlertDays() != null && command.defaultExpiryAlertDays() <= 0)
            throw new IllegalArgumentException("defaultExpiryAlertDays must be positive");
        if (command.afterSalePage() != null && !command.afterSalePage().isBlank()
                && !AFTER_SALE_PAGES.contains(command.afterSalePage()))
            throw new IllegalArgumentException("afterSalePage is invalid");
        if (Boolean.TRUE.equals(command.autoPrintReceipt()) && command.receiptPrinterId() == null)
            throw new IllegalArgumentException("A receipt printer is required when automatic printing is enabled");
        if (Boolean.FALSE.equals(command.fefoEnabled()))
            throw new IllegalArgumentException("FEFO stock allocation must remain enabled for the current POS batch-allocation flow");
        if (Boolean.FALSE.equals(command.blockExpiredSales()))
            throw new IllegalArgumentException("Blocking expired sales must remain enabled");
        if (command.maximumConcurrentShiftsPerRegister() == null || command.maximumConcurrentShiftsPerRegister() != 1)
            throw new IllegalArgumentException("maximumConcurrentShiftsPerRegister must be 1 because a register may have only one Open shift");
        if (Boolean.FALSE.equals(command.openShiftRequiredForCashTransactions()))
            throw new IllegalArgumentException("An Open cashier shift is required for physical cash operations");
        if (Boolean.FALSE.equals(command.handoverRequired()))
            throw new IllegalArgumentException("Cash handover must remain enabled because the current shift-close flow requires a closing destination");
        if (Boolean.TRUE.equals(command.scheduledJobsEnabled()))
            throw new IllegalArgumentException("Scheduled background jobs cannot be enabled because the current project ZIP contains no documented scheduled job implementation");
        if (command.openingFloatPolicy() == com.spark.falcon.settings.entity.enumtype.OpeningFloatPolicy.FIXED_AMOUNT
                && (command.fixedOpeningFloatAmount() == null || command.fixedOpeningFloatAmount().signum() < 0))
            throw new IllegalArgumentException("A non-negative fixed opening float amount is required");
        if (Boolean.TRUE.equals(command.automaticInvoiceSmsEnabled())
                && (!Boolean.TRUE.equals(command.smsNotificationsEnabled()) || command.smsGateway() == null || command.smsGateway().isBlank()))
            throw new IllegalArgumentException("Automatic invoice SMS requires enabled SMS notifications and a gateway");
    }

    public void validateAssignments(Set<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty())
            throw new IllegalArgumentException("At least one branch assignment is required");
        if (branchIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("branchIds contains an invalid branch id");
    }

    public String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
