package com.spark.falcon.branch.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.exception.BranchBusinessAccessDeniedException;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.businesssetup.service.BusinessSetupService;
import com.spark.falcon.user.dto.response.UserAccessResponse;
import com.spark.falcon.user.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchContextService {
    private final BusinessSetupService businessSetupService;
    private final BranchAccessService branchAccessService;
    private final UserAccessService userAccessService;
    private final ActiveBranchSession activeBranchSession;

    public BusinessSetupResponse resolveOwnerSetup(Long ownerId) {
        BusinessSetupResponse base = baseSetup(ownerId);
        BranchAccessResponse branch = selectedActiveBranch(base.businessId());
        if (branch == null) {
            branch = branchAccessService.findActiveByBusinessIdAndBranchId(base.businessId(), base.branchId())
                    .orElseGet(() -> branchAccessService.findActiveByBusinessId(base.businessId()).stream()
                            .findFirst()
                            .orElseThrow(BranchBusinessAccessDeniedException::new));
            activeBranchSession.select(base.businessId(), branch.branchId());
        }
        return withBranch(base, branch);
    }

    public BusinessSetupResponse resolveOwnerManagementSetup(Long ownerId) {
        BusinessSetupResponse base = baseSetup(ownerId);
        BranchAccessResponse branch = selectedActiveBranch(base.businessId());
        if (branch == null) {
            branch = branchAccessService.findActiveByBusinessIdAndBranchId(base.businessId(), base.branchId())
                    .orElseGet(() -> branchAccessService.findActiveByBusinessId(base.businessId()).stream()
                            .findFirst()
                            .orElse(null));
            if (branch != null) activeBranchSession.select(base.businessId(), branch.branchId());
        }
        return branch == null ? base : withBranch(base, branch);
    }

    public List<BranchAccessResponse> findOwnerSelectableBranches(Long ownerId) {
        BusinessSetupResponse base = baseSetup(ownerId);
        return branchAccessService.findActiveByBusinessId(base.businessId());
    }

    public void selectOwnerBranch(Long ownerId, Long branchId) {
        BusinessSetupResponse base = baseSetup(ownerId);
        BranchAccessResponse branch = branchAccessService.findActiveByBusinessIdAndBranchId(base.businessId(), branchId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
        activeBranchSession.select(base.businessId(), branch.branchId());
    }

    public List<BranchAccessResponse> findAssignedSelectableBranches(Long businessId, Long userId) {
        UserAccessResponse user = userAccessService.findActiveByBusinessIdAndUserId(businessId, userId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
        return branchAccessService.findActiveByBusinessId(businessId).stream()
                .filter(branch -> user.branchIds().contains(branch.branchId()))
                .toList();
    }

    public void selectAssignedBranch(Long businessId, Long userId, Long branchId) {
        if (!userAccessService.hasActiveBranchAccess(businessId, userId, branchId)) {
            throw new BranchBusinessAccessDeniedException();
        }
        BranchAccessResponse branch = branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
        activeBranchSession.select(businessId, branch.branchId());
    }

    public void clearIfSelected(Long businessId, Long branchId) {
        if (activeBranchSession.isSelected(businessId, branchId)) activeBranchSession.clear();
    }

    public Long currentBranchId(Long ownerId) {
        return resolveOwnerSetup(ownerId).branchId();
    }

    private BusinessSetupResponse baseSetup(Long ownerId) {
        return businessSetupService.findByOwner(ownerId)
                .orElseThrow(BranchBusinessAccessDeniedException::new);
    }

    private BranchAccessResponse selectedActiveBranch(Long businessId) {
        if (activeBranchSession.getBranchId() == null || !businessId.equals(activeBranchSession.getBusinessId())) {
            activeBranchSession.clear();
            return null;
        }
        return branchAccessService.findActiveByBusinessIdAndBranchId(businessId, activeBranchSession.getBranchId())
                .orElseGet(() -> {
                    activeBranchSession.clear();
                    return null;
                });
    }

    private BusinessSetupResponse withBranch(BusinessSetupResponse base, BranchAccessResponse branch) {
        return new BusinessSetupResponse(base.businessId(), branch.branchId(), base.businessName(), base.businessCode(),
                branch.branchName(), branch.branchCode(), base.ownerName());
    }
}
