package com.spark.falcon.settings.mapper;

import com.spark.falcon.branch.dto.command.UpdateBranchProfileCommand;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.settings.dto.command.*;
import com.spark.falcon.settings.dto.request.*;
import com.spark.falcon.settings.dto.response.*;
import com.spark.falcon.settings.entity.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class SettingsMapper {
    public BranchSettingsRequest toRequest(BranchSettingsResponse response) {
        BranchSettingsRequest request = new BranchSettingsRequest();
        request.setName(response.name());
        request.setCode(response.code());
        request.setCountry(response.country());
        request.setEmail(response.email());
        request.setPhone(response.phone());
        request.setTimeZone(response.timeZone());
        request.setAddress(response.address());
        request.setCity(response.city());
        request.setStateDivision(response.stateDivision());
        request.setPostalCode(response.postalCode());
        request.setVatBinNumber(response.vatBinNumber());
        request.setDefaultTaxRate(response.defaultTaxRate());
        request.setLowStockAlertQuantity(response.lowStockAlertQuantity());
        request.setRecordsPerPage(response.recordsPerPage());
        request.setReceiptFooter(response.receiptFooter());
        request.setDefaultCashierName(response.defaultCashierName());
        request.setDraftHeldEditWindowMinutes(response.draftHeldEditWindowMinutes());
        request.setAfterSalePage(response.afterSalePage());
        request.setAutoPrintReceipt(response.autoPrintReceipt());
        request.setReceiptPrinterId(response.receiptPrinterId());
        request.setPosSoundEffectsEnabled(response.posSoundEffectsEnabled());
        request.setDefaultExpiryAlertDays(response.defaultExpiryAlertDays());
        request.setFefoEnabled(response.fefoEnabled());
        request.setBlockExpiredSales(response.blockExpiredSales());
        request.setNearExpiryWarningsEnabled(response.nearExpiryWarningsEnabled());
        request.setAllowMissingExpiryInformation(response.allowMissingExpiryInformation());
        request.setDefaultDepositAccountReference(response.defaultDepositAccountReference());
        request.setOpenShiftRequiredForCashTransactions(response.openShiftRequiredForCashTransactions());
        request.setMaximumConcurrentShiftsPerRegister(response.maximumConcurrentShiftsPerRegister());
        request.setCashierMultipleShiftsAllowed(response.cashierMultipleShiftsAllowed());
        request.setOpeningFloatPolicy(response.openingFloatPolicy());
        request.setFixedOpeningFloatAmount(response.fixedOpeningFloatAmount());
        request.setAllowedOpeningSources(response.allowedOpeningSources());
        request.setAllowedClosingDestinations(response.allowedClosingDestinations());
        request.setCashDropRequired(response.cashDropRequired());
        request.setHandoverRequired(response.handoverRequired());
        request.setDenominationCountRequired(response.denominationCountRequired());
        request.setVarianceApprovalThreshold(response.varianceApprovalThreshold());
        request.setHighValueTransferApprovalThreshold(response.highValueTransferApprovalThreshold());
        request.setSegregationOfDutiesRequired(response.segregationOfDutiesRequired());
        request.setSmsGateway(response.smsGateway());
        request.setSmsNotificationsEnabled(response.smsNotificationsEnabled());
        request.setAutomaticInvoiceSmsEnabled(response.automaticInvoiceSmsEnabled());
        request.setGeneralExpirationReminderDays(response.generalExpirationReminderDays());
        request.setExpirationNotificationRecipients(response.expirationNotificationRecipients());
        request.setBusinessLogoReference(response.businessLogoReference());
        request.setFaviconReference(response.faviconReference());
        request.setScheduledJobsEnabled(response.scheduledJobsEnabled());
        return request;
    }

    public UpdateBranchSettingsCommand toCommand(Long ownerId, Long branchId, BranchSettingsRequest request) {
        return new UpdateBranchSettingsCommand(ownerId, branchId, request.getName(), request.getCode(),
                request.getCountry(), request.getEmail(), request.getPhone(), request.getTimeZone(), request.getAddress(),
                request.getCity(), request.getStateDivision(), request.getPostalCode(), request.getVatBinNumber(),
                request.getDefaultTaxRate(), request.getLowStockAlertQuantity(), request.getRecordsPerPage(),
                request.getReceiptFooter(), request.getDefaultCashierName(), request.getDraftHeldEditWindowMinutes(),
                request.getAfterSalePage(), request.getAutoPrintReceipt(), request.getReceiptPrinterId(), request.getPosSoundEffectsEnabled(),
                request.getDefaultExpiryAlertDays(), request.getFefoEnabled(), request.getBlockExpiredSales(),
                request.getNearExpiryWarningsEnabled(), request.getAllowMissingExpiryInformation(),
                request.getDefaultDepositAccountReference(), request.getOpenShiftRequiredForCashTransactions(),
                request.getMaximumConcurrentShiftsPerRegister(), request.getCashierMultipleShiftsAllowed(),
                request.getOpeningFloatPolicy(), request.getFixedOpeningFloatAmount(), request.getAllowedOpeningSources(),
                request.getAllowedClosingDestinations(), request.getCashDropRequired(), request.getHandoverRequired(),
                request.getDenominationCountRequired(), request.getVarianceApprovalThreshold(),
                request.getHighValueTransferApprovalThreshold(), request.getSegregationOfDutiesRequired(),
                request.getSmsGateway(), request.getSmsNotificationsEnabled(), request.getAutomaticInvoiceSmsEnabled(),
                request.getGeneralExpirationReminderDays(), request.getExpirationNotificationRecipients(),
                request.getBusinessLogoReference(), request.getFaviconReference(), request.getScheduledJobsEnabled());
    }

    public UpdateBranchProfileCommand toBranchProfileCommand(UpdateBranchSettingsCommand command) {
        return new UpdateBranchProfileCommand(command.ownerId(), command.branchId(), command.name(), command.code(),
                command.country(), command.email(), command.phone(), command.timeZone(), command.address(),
                command.city(), command.stateDivision(), command.postalCode(), command.vatBinNumber(),
                command.defaultTaxRate(), command.lowStockAlertQuantity(), command.recordsPerPage(), command.receiptFooter());
    }

    public CreatePaymentMethodCommand toCreateCommand(Long ownerId, PaymentMethodRequest request) {
        return new CreatePaymentMethodCommand(ownerId, request.getName(), request.getCode(), request.getDescription(),
                request.isCash(), request.isTransactionReferenceRequired(), request.getReconciliationChannelReference(),
                request.getReconciliationAccountReference(), request.getDisplayOrder(), copy(request.getBranchIds()), request.getStatus());
    }

    public UpdatePaymentMethodCommand toUpdateCommand(Long ownerId, Long paymentMethodId, PaymentMethodRequest request) {
        return new UpdatePaymentMethodCommand(ownerId, paymentMethodId, request.getName(), request.getCode(),
                request.getDescription(), request.isCash(), request.isTransactionReferenceRequired(),
                request.getReconciliationChannelReference(), request.getReconciliationAccountReference(),
                request.getDisplayOrder(), copy(request.getBranchIds()), request.getStatus());
    }

    public CreateUnitCommand toCreateCommand(Long ownerId, UnitRequest request) {
        return new CreateUnitCommand(ownerId, request.getName(), request.getCode(), request.getDescription(),
                request.getDisplayOrder(), copy(request.getBranchIds()), request.getStatus());
    }

    public UpdateUnitCommand toUpdateCommand(Long ownerId, Long unitId, UnitRequest request) {
        return new UpdateUnitCommand(ownerId, unitId, request.getName(), request.getCode(), request.getDescription(),
                request.getDisplayOrder(), copy(request.getBranchIds()), request.getStatus());
    }

    public CreateTaxRateCommand toCreateCommand(Long ownerId, TaxRateRequest request) {
        return new CreateTaxRateCommand(ownerId, request.getName(), request.getCode(), request.getRate(), request.getDisplayOrder(), request.getStatus());
    }

    public UpdateTaxRateCommand toUpdateCommand(Long ownerId, Long taxRateId, TaxRateRequest request) {
        return new UpdateTaxRateCommand(ownerId, taxRateId, request.getName(), request.getCode(), request.getRate(), request.getDisplayOrder(), request.getStatus());
    }

    public BranchSettingsResponse toResponse(BranchResponse branch, BranchSettings settings) {
        return new BranchSettingsResponse(branch.id(), branch.businessId(), branch.name(), branch.code(), branch.country(),
                branch.email(), branch.phone(), branch.timeZone(), branch.address(), branch.city(), branch.stateDivision(),
                branch.postalCode(), branch.vatBinNumber(), branch.defaultTaxRate(), branch.lowStockAlertQuantity(),
                branch.recordsPerPage(), branch.receiptFooter(), settings == null ? null : settings.getDefaultCashierName(),
                settings == null ? null : settings.getDraftHeldEditWindowMinutes(),
                settings == null ? null : settings.getAfterSalePage(),
                settings == null ? null : settings.getAutoPrintReceipt(),
                settings == null ? null : settings.getReceiptPrinterId(),
                settings == null ? null : settings.getPosSoundEffectsEnabled(),
                settings == null ? null : settings.getDefaultExpiryAlertDays(),
                settings == null || !Boolean.FALSE.equals(settings.getFefoEnabled()),
                settings == null || settings.isBlockExpiredSales(),
                settings == null ? null : settings.getNearExpiryWarningsEnabled(),
                settings == null ? null : settings.getAllowMissingExpiryInformation(),
                settings == null ? null : settings.getDefaultDepositAccountReference(),
                settings == null || settings.isOpenShiftRequiredForCashTransactions(),
                settings == null || settings.getMaximumConcurrentShiftsPerRegister() == null
                        ? 1 : settings.getMaximumConcurrentShiftsPerRegister(),
                settings != null && settings.isCashierMultipleShiftsAllowed(),
                settings == null ? com.spark.falcon.settings.entity.enumtype.OpeningFloatPolicy.OPTIONAL : settings.getOpeningFloatPolicy(),
                settings == null ? null : settings.getFixedOpeningFloatAmount(),
                settings == null ? null : settings.getAllowedOpeningSources(),
                settings == null ? null : settings.getAllowedClosingDestinations(),
                settings != null && settings.isCashDropRequired(), true,
                settings != null && settings.isDenominationCountRequired(),
                settings == null ? null : settings.getVarianceApprovalThreshold(),
                settings == null ? null : settings.getHighValueTransferApprovalThreshold(),
                settings != null && settings.isSegregationOfDutiesRequired(),
                settings == null ? null : settings.getSmsGateway(),
                settings != null && settings.isSmsNotificationsEnabled(),
                settings != null && settings.isAutomaticInvoiceSmsEnabled(),
                settings == null ? null : settings.getGeneralExpirationReminderDays(),
                settings == null ? null : settings.getExpirationNotificationRecipients(),
                settings == null ? null : settings.getBusinessLogoReference(),
                settings == null ? null : settings.getFaviconReference(),
                settings != null && settings.isScheduledJobsEnabled());
    }

    public PaymentMethodResponse toResponse(PaymentMethod method, Set<Long> branchIds) {
        return new PaymentMethodResponse(method.getId(), method.getBusinessId(), method.getName(), method.getCode(),
                method.getDescription(), method.isCash(), method.isTransactionReferenceRequired(),
                method.getReconciliationChannelReference(), method.getReconciliationAccountReference(), method.getDisplayOrder(),
                method.getStatus(), Set.copyOf(branchIds), method.isArchived());
    }

    public UnitResponse toResponse(Unit unit, Set<Long> branchIds) {
        return new UnitResponse(unit.getId(), unit.getBusinessId(), unit.getName(), unit.getCode(), unit.getDescription(),
                unit.getDisplayOrder(), unit.getStatus(), Set.copyOf(branchIds), unit.isArchived());
    }

    public TaxRateResponse toResponse(TaxRate taxRate) {
        return new TaxRateResponse(taxRate.getId(), taxRate.getBusinessId(), taxRate.getName(), taxRate.getCode(),
                taxRate.getRate(), taxRate.getDisplayOrder(), taxRate.getStatus(), taxRate.isArchived());
    }

    private static Set<Long> copy(Set<Long> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
