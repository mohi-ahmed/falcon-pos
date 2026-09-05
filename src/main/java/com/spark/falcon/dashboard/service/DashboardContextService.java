package com.spark.falcon.dashboard.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.exception.BranchBusinessAccessDeniedException;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.dashboard.dto.DashboardContextResponse;
import com.spark.falcon.identity.security.OwnerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardContextService {

    private final BranchContextService branchContextService;

    public DashboardContextResponse resolve(OwnerPrincipal principal) {
        if (principal == null) throw new BranchBusinessAccessDeniedException();

        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        List<BranchAccessResponse> accessible = principal.isOwner()
                ? branchContextService.findOwnerSelectableBranches(principal.ownerId())
                : branchContextService.findAssignedSelectableBranches(principal.businessId(), principal.staffUserId());
        if (accessible.isEmpty()) throw new BranchBusinessAccessDeniedException();

        Long selectedBranchId = setup.branchId();
        BranchAccessResponse active = accessible.stream()
                .filter(branch -> branch.branchId().equals(selectedBranchId))
                .findFirst()
                .orElseGet(() -> selectFirstAccessible(principal, accessible.get(0)));

        if (!active.branchId().equals(setup.branchId())) {
            setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        }

        String currentUser = principal.isOwner() ? setup.ownerName() : principal.username();
        String currentRole = principal.isOwner() ? "Primary Owner"
                : principal.userGroupName() == null || principal.userGroupName().isBlank() ? "Staff" : principal.userGroupName();
        return new DashboardContextResponse(setup, active, List.copyOf(accessible), currentUser, currentRole);
    }

    private BranchAccessResponse selectFirstAccessible(OwnerPrincipal principal, BranchAccessResponse branch) {
        if (principal.isOwner()) {
            branchContextService.selectOwnerBranch(principal.ownerId(), branch.branchId());
        } else {
            branchContextService.selectAssignedBranch(principal.businessId(), principal.staffUserId(), branch.branchId());
        }
        return branch;
    }
}
