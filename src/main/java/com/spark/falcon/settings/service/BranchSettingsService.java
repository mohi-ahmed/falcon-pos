package com.spark.falcon.settings.service;

import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.settings.dto.command.UpdateBranchSettingsCommand;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.entity.BranchSettings;
import com.spark.falcon.settings.entity.SettingsAuditEvent;
import com.spark.falcon.settings.entity.enumtype.SettingsAuditAction;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.BranchSettingsRepository;
import com.spark.falcon.settings.repository.SettingsAuditEventRepository;
import com.spark.falcon.settings.validation.SettingsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BranchSettingsService {
    private final BusinessAccessService businessAccessService;
    private final BranchService branchService;
    private final BranchSettingsRepository branchSettingsRepository;
    private final SettingsAuditEventRepository auditRepository;
    private final SettingsMapper mapper;
    private final SettingsValidator validator;
    private final PrinterService printerService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<BranchSettingsResponse> findByOwnerAndBranch(Long ownerId, Long branchId) {
        BusinessAccessResponse business = businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(SettingsAccessDeniedException::new);
        Optional<BranchResponse> branch = branchService.findByBusinessIdAndBranchId(business.businessId(), branchId);
        if (branch.isEmpty()) return Optional.empty();
        return Optional.of(mapper.toResponse(branch.get(),
                branchSettingsRepository.findByBusinessIdAndBranchId(business.businessId(), branchId).orElse(null)));
    }

    @Transactional
    public BranchSettingsResponse update(UpdateBranchSettingsCommand command) {
        validator.validateBranchSettings(command);
        BusinessAccessResponse business = businessAccessService.findByOwnerId(command.ownerId())
                .orElseThrow(SettingsAccessDeniedException::new);
        if (branchService.findByBusinessIdAndBranchId(business.businessId(), command.branchId()).isEmpty())
            throw new SettingsAccessDeniedException();
        if (command.receiptPrinterId() != null
                && printerService.findActiveForBranch(command.ownerId(), command.branchId()).stream()
                .noneMatch(printer -> printer.id().equals(command.receiptPrinterId())))
            throw new SettingsAccessDeniedException();

        BranchResponse branch = branchService.updateSettingsProfile(mapper.toBranchProfileCommand(command));
        Instant now = Instant.now(clock);
        BranchSettings settings = branchSettingsRepository.findByBusinessIdAndBranchId(business.businessId(), command.branchId())
                .orElseGet(() -> BranchSettings.create(business.businessId(), command.branchId(), now));
        settings.update(command.defaultCashierName(), command.draftHeldEditWindowMinutes(), command.afterSalePage(),
                command.autoPrintReceipt(), command.receiptPrinterId(), command.posSoundEffectsEnabled(), command.defaultExpiryAlertDays(),
                command.fefoEnabled(), command.blockExpiredSales(), command.nearExpiryWarningsEnabled(),
                command.allowMissingExpiryInformation(), now);
        settings.updateDocumentedConfiguration(command.defaultDepositAccountReference(),
                command.openShiftRequiredForCashTransactions(), command.maximumConcurrentShiftsPerRegister(),
                command.cashierMultipleShiftsAllowed(), command.openingFloatPolicy(), command.fixedOpeningFloatAmount(),
                command.allowedOpeningSources(), command.allowedClosingDestinations(), command.cashDropRequired(),
                command.handoverRequired(), command.denominationCountRequired(), command.varianceApprovalThreshold(),
                command.highValueTransferApprovalThreshold(), command.segregationOfDutiesRequired(),
                command.smsGateway(), command.smsNotificationsEnabled(), command.automaticInvoiceSmsEnabled(),
                command.generalExpirationReminderDays(), command.expirationNotificationRecipients(),
                command.businessLogoReference(), command.faviconReference(), command.scheduledJobsEnabled(), now);
        BranchSettings saved = branchSettingsRepository.saveAndFlush(settings);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), command.branchId(), command.ownerId(),
                SettingsAuditAction.BRANCH_OPERATIONAL_SETTINGS_UPDATED, "BRANCH_SETTINGS", saved.getId(), now));
        return mapper.toResponse(branch, saved);
    }
}
