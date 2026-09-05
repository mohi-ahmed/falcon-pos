package com.spark.falcon.businesssetup.service;

import com.spark.falcon.businesssetup.dto.command.CreateBusinessSetupCommand;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.dto.command.CreateInitialBranchCommand;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.businesssetup.entity.*;
import com.spark.falcon.businesssetup.exception.*;
import com.spark.falcon.businesssetup.repository.*;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessSetupService {
    private final OwnerRepository ownerRepository;
    private final BusinessRepository businessRepository;
    private final BranchService branchService;
    private final BusinessSetupAuditRepository auditRepository;
    private final Clock clock;

    @Transactional
    public BusinessSetupResponse create(CreateBusinessSetupCommand c) {
        Optional<Business> existing = businessRepository.findByOwnerId(c.ownerId());
        if (existing.isPresent()) {
            if (existing.get().getSetupIdempotencyKey().equals(c.idempotencyKey())) return response(existing.get());
            throw new BusinessAlreadyConfiguredException();
        }
        if (businessRepository.existsByCodeIgnoreCase(c.businessCode())) throw new BusinessCodeAlreadyUsedException();
        Owner owner = ownerRepository.findById(c.ownerId()).orElseThrow();
        Instant now = Instant.now(clock);
        Business business = businessRepository.save(Business.create(owner, c.businessName(), c.businessCode(), c.businessType(),
                c.businessEmail(), c.businessPhone(), c.idempotencyKey(), now));
        BranchResponse branch = branchService.createInitialBranch(new CreateInitialBranchCommand(
                business.getId(), owner.getId(), c.idempotencyKey(), c.branchName(), c.branchCode(), c.country(),
                c.branchEmail(), c.branchPhone(), c.timezone(), c.currency(), c.address(), c.city(), c.stateDivision(),
                c.postalCode(), c.vatBinNumber(), c.defaultTaxRate(), c.lowStockAlertQuantity(), c.recordsPerPage(),
                c.receiptFooter()));
        auditRepository.save(BusinessSetupAudit.created(owner.getId(), business.getId(), branch.id(), now));
        return response(business, branch);
    }

    @Transactional(readOnly = true)
    public Optional<BusinessSetupResponse> findByOwner(Long ownerId) {
        return businessRepository.findByOwnerId(ownerId).map(this::response);
    }

    private BusinessSetupResponse response(Business business) {
        BranchResponse branch = branchService.findFirstByBusinessId(business.getId()).orElseThrow();
        return response(business, branch);
    }

    private BusinessSetupResponse response(Business business, BranchResponse branch) {
        return new BusinessSetupResponse(
                business.getId(),
                branch.id(),
                business.getName(),
                business.getCode(),
                branch.name(),
                branch.code(),
                business.getOwner().getFullName()
        );
    }
}
