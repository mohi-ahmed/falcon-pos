package com.spark.falcon.settings.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.settings.dto.command.CreatePaymentMethodCommand;
import com.spark.falcon.settings.dto.command.UpdatePaymentMethodCommand;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.settings.entity.PaymentMethodBranch;
import com.spark.falcon.settings.entity.SettingsAuditEvent;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.SettingsAuditAction;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsCodeAlreadyUsedException;
import com.spark.falcon.settings.exception.SettingsNotFoundException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.repository.PaymentMethodBranchRepository;
import com.spark.falcon.settings.repository.PaymentMethodRepository;
import com.spark.falcon.settings.repository.SettingsAuditEventRepository;
import com.spark.falcon.settings.validation.SettingsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentMethodBranchRepository assignmentRepository;
    private final SettingsAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SettingsMapper mapper;
    private final SettingsValidator validator;
    private final Clock clock;

    @Transactional
    public PaymentMethodResponse create(CreatePaymentMethodCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validateAssignments(command.branchIds());
        validateBranches(business.businessId(), command.branchIds());
        String code = validator.normalizeCode(command.code());
        if (paymentMethodRepository.existsByBusinessIdAndCodeIgnoreCase(business.businessId(), code))
            throw new SettingsCodeAlreadyUsedException("Payment method", code);

        Instant now = Instant.now(clock);
        PaymentMethod method = paymentMethodRepository.saveAndFlush(PaymentMethod.create(business.businessId(),
                command.name(), code, command.description(), command.cash(), command.transactionReferenceRequired(),
                command.reconciliationChannelReference(), command.reconciliationAccountReference(), command.displayOrder(), now));
        if (command.status() == ConfigurationStatus.INACTIVE) method.changeStatus(ConfigurationStatus.INACTIVE, now);
        Set<Long> branchIds = syncAssignments(method.getId(), command.branchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.PAYMENT_METHOD_CREATED, "PAYMENT_METHOD", method.getId(), now));
        return mapper.toResponse(method, branchIds);
    }

    @Transactional
    public PaymentMethodResponse update(UpdatePaymentMethodCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validateAssignments(command.branchIds());
        validateBranches(business.businessId(), command.branchIds());
        PaymentMethod method = paymentMethodRepository.findByIdAndBusinessId(command.paymentMethodId(), business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Payment method"));
        String code = validator.normalizeCode(command.code());
        if (paymentMethodRepository.existsByBusinessIdAndCodeIgnoreCaseAndIdNot(
                business.businessId(), code, command.paymentMethodId()))
            throw new SettingsCodeAlreadyUsedException("Payment method", code);

        Instant now = Instant.now(clock);
        method.update(command.name(), code, command.description(), command.cash(),
                command.transactionReferenceRequired(), command.reconciliationChannelReference(),
                command.reconciliationAccountReference(), command.displayOrder(), now);
        PaymentMethod saved = paymentMethodRepository.saveAndFlush(method);
        Set<Long> branchIds = syncAssignments(saved.getId(), command.branchIds(), now);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, command.ownerId(),
                SettingsAuditAction.PAYMENT_METHOD_UPDATED, "PAYMENT_METHOD", saved.getId(), now));
        return mapper.toResponse(saved, branchIds);
    }

    @Transactional
    public PaymentMethodResponse changeStatus(Long ownerId, Long paymentMethodId, ConfigurationStatus status) {
        BusinessAccessResponse business = business(ownerId);
        PaymentMethod method = paymentMethodRepository.findByIdAndBusinessId(paymentMethodId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Payment method"));
        Instant now = Instant.now(clock);
        method.changeStatus(status, now);
        PaymentMethod saved = paymentMethodRepository.saveAndFlush(method);
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PAYMENT_METHOD_STATUS_CHANGED, "PAYMENT_METHOD", saved.getId(), now));
        return mapper.toResponse(saved, activeBranchIds(saved.getId()));
    }

    @Transactional
    public void archive(Long ownerId, Long paymentMethodId) {
        BusinessAccessResponse business = business(ownerId);
        PaymentMethod method = paymentMethodRepository.findByIdAndBusinessId(paymentMethodId, business.businessId())
                .orElseThrow(() -> new SettingsNotFoundException("Payment method"));
        Instant now = Instant.now(clock);
        method.archive(now);
        paymentMethodRepository.saveAndFlush(method);
        for (PaymentMethodBranch assignment : assignmentRepository.findByPaymentMethodId(paymentMethodId)) {
            if (assignment.isActive()) assignment.deactivate(now);
        }
        auditRepository.save(SettingsAuditEvent.record(business.businessId(), null, ownerId,
                SettingsAuditAction.PAYMENT_METHOD_ARCHIVED, "PAYMENT_METHOD", paymentMethodId, now));
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> findAll(Long ownerId) {
        BusinessAccessResponse business = business(ownerId);
        return paymentMethodRepository.findManageableByBusinessId(business.businessId())
                .stream().map(method -> mapper.toResponse(method, activeBranchIds(method.getId()))).toList();
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(SettingsAccessDeniedException::new);
    }

    private void validateBranches(Long businessId, Set<Long> branchIds) {
        for (Long branchId : branchIds) {
            if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty())
                throw new SettingsAccessDeniedException();
        }
    }

    private Set<Long> syncAssignments(Long methodId, Set<Long> requested, Instant now) {
        Map<Long, PaymentMethodBranch> existing = new LinkedHashMap<>();
        for (PaymentMethodBranch assignment : assignmentRepository.findByPaymentMethodId(methodId))
            existing.put(assignment.getBranchId(), assignment);
        for (PaymentMethodBranch assignment : existing.values()) {
            if (requested.contains(assignment.getBranchId())) assignment.activate(now);
            else assignment.deactivate(now);
        }
        for (Long branchId : requested) {
            if (!existing.containsKey(branchId)) assignmentRepository.save(PaymentMethodBranch.assign(methodId, branchId, now));
        }
        assignmentRepository.flush();
        return new LinkedHashSet<>(requested);
    }

    private Set<Long> activeBranchIds(Long methodId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        assignmentRepository.findByPaymentMethodId(methodId).stream().filter(PaymentMethodBranch::isActive)
                .map(PaymentMethodBranch::getBranchId).forEach(ids::add);
        return ids;
    }
}
