package com.spark.falcon.branch.service;

import com.spark.falcon.branch.dto.command.ChangeBranchStatusCommand;
import com.spark.falcon.branch.dto.command.CreateBranchCommand;
import com.spark.falcon.branch.dto.command.CreateInitialBranchCommand;
import com.spark.falcon.branch.dto.command.UpdateBranchProfileCommand;
import com.spark.falcon.branch.dto.response.BranchAuditResponse;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.branch.entity.BranchAuditEvent;
import com.spark.falcon.branch.entity.enumtype.BranchAuditAction;
import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import com.spark.falcon.branch.exception.BranchBusinessAccessDeniedException;
import com.spark.falcon.branch.exception.BranchCodeAlreadyUsedException;
import com.spark.falcon.branch.mapper.BranchMapper;
import com.spark.falcon.branch.repository.BranchAuditEventRepository;
import com.spark.falcon.branch.repository.BranchRepository;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.identity.security.CurrentActorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branchRepository;
    private final BranchAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchMapper branchMapper;
    private final Clock clock;
    private final CurrentActorService currentActorService;

    @Transactional
    public BranchResponse create(CreateBranchCommand command) {
        BusinessAccessResponse business = business(command.ownerId());

        Optional<Branch> previousResult = branchRepository.findByBusinessIdAndCreateIdempotencyKey(
                business.businessId(), command.idempotencyKey());
        if (previousResult.isPresent()) return branchMapper.toResponse(previousResult.get());

        if (branchRepository.existsByBusinessIdAndCodeIgnoreCase(business.businessId(), command.code())) {
            throw new BranchCodeAlreadyUsedException(command.code());
        }

        Instant now = Instant.now(clock);
        Branch branch = Branch.create(business.businessId(), command.ownerId(), command.idempotencyKey(),
                command.name(), command.code(), command.country(), command.email(), command.phone(),
                command.timeZone(), command.currency(), command.address(), command.city(), command.stateDivision(),
                command.postalCode(), command.vatBinNumber(), command.defaultTaxRate(),
                command.lowStockAlertQuantity(), command.recordsPerPage(), command.receiptFooter(), now);

        Branch savedBranch = branchRepository.saveAndFlush(branch);
        auditRepository.save(BranchAuditEvent.record(business.businessId(), savedBranch.getId(), currentActorService.actorId(command.ownerId()),
                BranchAuditAction.CREATED, null, snapshot(savedBranch), now));
        return branchMapper.toResponse(savedBranch);
    }

    @Transactional
    public BranchResponse createInitialBranch(CreateInitialBranchCommand command) {
        Optional<Branch> previousResult = branchRepository.findByBusinessIdAndCreateIdempotencyKey(
                command.businessId(), command.idempotencyKey());
        if (previousResult.isPresent()) return branchMapper.toResponse(previousResult.get());

        if (branchRepository.existsByBusinessIdAndCodeIgnoreCase(command.businessId(), command.code())) {
            throw new BranchCodeAlreadyUsedException(command.code());
        }

        Instant now = Instant.now(clock);
        Branch branch = Branch.create(command.businessId(), command.ownerId(), command.idempotencyKey(), command.name(),
                command.code(), command.country(), command.email(), command.phone(), command.timeZone(),
                command.currency(), command.address(), command.city(), command.stateDivision(), command.postalCode(),
                command.vatBinNumber(), command.defaultTaxRate(), command.lowStockAlertQuantity(),
                command.recordsPerPage(), command.receiptFooter(), now);
        Branch savedBranch = branchRepository.saveAndFlush(branch);
        auditRepository.save(BranchAuditEvent.record(command.businessId(), savedBranch.getId(), currentActorService.actorId(command.ownerId()),
                BranchAuditAction.INITIAL_BRANCH_CREATED, null, snapshot(savedBranch), now));
        return branchMapper.toResponse(savedBranch);
    }

    @Transactional
    public BranchResponse updateSettingsProfile(UpdateBranchProfileCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Branch branch = ownedBranch(business.businessId(), command.branchId());

        if (branchRepository.existsByBusinessIdAndCodeIgnoreCaseAndIdNot(
                business.businessId(), command.code(), command.branchId())) {
            throw new BranchCodeAlreadyUsedException(command.code());
        }

        String beforeSnapshot = snapshot(branch);
        branch.updateSettingsProfile(command.name(), command.code(), command.country(), command.email(),
                command.phone(), command.timeZone(), command.address(), command.city(), command.stateDivision(),
                command.postalCode(), command.vatBinNumber(), command.defaultTaxRate(),
                command.lowStockAlertQuantity(), command.recordsPerPage(), command.receiptFooter());

        Instant now = Instant.now(clock);
        Branch savedBranch = branchRepository.saveAndFlush(branch);
        auditRepository.save(BranchAuditEvent.record(business.businessId(), savedBranch.getId(), currentActorService.actorId(command.ownerId()),
                BranchAuditAction.SETTINGS_UPDATED, beforeSnapshot, snapshot(savedBranch), now));
        return branchMapper.toResponse(savedBranch);
    }

    @Transactional
    public BranchResponse changeStatus(ChangeBranchStatusCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Branch branch = ownedBranch(business.businessId(), command.branchId());
        if (branch.getStatus() == command.status()) return branchMapper.toResponse(branch);

        String beforeSnapshot = snapshot(branch);
        BranchAuditAction action;
        if (command.status() == BranchStatus.ACTIVE) {
            branch.activate();
            action = BranchAuditAction.ACTIVATED;
        } else {
            branch.deactivate();
            action = BranchAuditAction.DEACTIVATED;
        }

        Instant now = Instant.now(clock);
        Branch savedBranch = branchRepository.saveAndFlush(branch);
        auditRepository.save(BranchAuditEvent.record(business.businessId(), savedBranch.getId(), currentActorService.actorId(command.ownerId()),
                action, beforeSnapshot, snapshot(savedBranch), now));
        return branchMapper.toResponse(savedBranch);
    }

    public Page<BranchResponse> findAll(Long ownerId, String keyword, Pageable pageable) {
        BusinessAccessResponse business = business(ownerId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<Branch> branches = normalizedKeyword.isBlank()
                ? branchRepository.findByBusinessId(business.businessId(), pageable)
                : branchRepository.searchByBusinessId(business.businessId(), normalizedKeyword, pageable);
        return branches.map(branchMapper::toResponse);
    }

    public Page<BranchResponse> findAllAssigned(Long businessId, Set<Long> branchIds, String keyword, Pageable pageable) {
        if (businessId == null || branchIds == null || branchIds.isEmpty()) return Page.empty(pageable);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<Branch> branches = normalizedKeyword.isBlank()
                ? branchRepository.findByBusinessIdAndIdIn(businessId, branchIds, pageable)
                : branchRepository.searchByBusinessIdAndIdIn(businessId, branchIds, normalizedKeyword, pageable);
        return branches.map(branchMapper::toResponse);
    }

    public BranchResponse findOwnedById(Long ownerId, Long branchId) {
        BusinessAccessResponse business = business(ownerId);
        return branchMapper.toResponse(ownedBranch(business.businessId(), branchId));
    }

    public List<BranchAuditResponse> findAuditHistory(Long ownerId, Long branchId) {
        BusinessAccessResponse business = business(ownerId);
        ownedBranch(business.businessId(), branchId);
        return auditRepository.findByBusinessIdAndBranchIdOrderByOccurredAtDescIdDesc(business.businessId(), branchId)
                .stream()
                .map(branchMapper::toAuditResponse)
                .toList();
    }

    public Optional<BranchResponse> findByBusinessIdAndBranchId(Long businessId, Long branchId) {
        return branchRepository.findByIdAndBusinessId(branchId, businessId).map(branchMapper::toResponse);
    }

    public Optional<BranchResponse> findFirstByBusinessId(Long businessId) {
        return branchRepository.findFirstByBusinessIdOrderByIdAsc(businessId).map(branchMapper::toResponse);
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
    }

    private Branch ownedBranch(Long businessId, Long branchId) {
        return branchRepository.findByIdAndBusinessId(branchId, businessId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
    }

    private String snapshot(Branch branch) {
        return new BranchSnapshot(branch.getName(), branch.getCode(), branch.getCountry(), branch.getEmail(),
                branch.getPhone(), branch.getTimeZone(), branch.getCurrency(), branch.getAddress(), branch.getCity(),
                branch.getStateDivision(), branch.getPostalCode(), branch.getVatBinNumber(), branch.getDefaultTaxRate(),
                branch.getLowStockAlertQuantity(), branch.getRecordsPerPage(), branch.getReceiptFooter(),
                branch.getStatus()).toString();
    }

    private record BranchSnapshot(
            String name,
            String code,
            String country,
            String email,
            String phone,
            String timeZone,
            String currency,
            String address,
            String city,
            String stateDivision,
            String postalCode,
            String vatBinNumber,
            BigDecimal defaultTaxRate,
            Integer lowStockAlertQuantity,
            Integer recordsPerPage,
            String receiptFooter,
            BranchStatus status
    ) {
    }
}
