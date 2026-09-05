package com.spark.falcon.analytics.service;

import com.spark.falcon.analytics.dto.AnalyticsContextResponse;
import com.spark.falcon.analytics.dto.AnalyticsReportType;
import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.user.entity.enumtype.SystemPermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsAccessService {

    private final BranchContextService branchContextService;

    public AnalyticsContextResponse overviewContext(OwnerPrincipal principal) {
        requireAnalyticsAccess(principal);
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        List<BranchAccessResponse> accessible = accessibleBranches(principal, setup.businessId());
        BranchAccessResponse active = accessible.stream()
                .filter(branch -> branch.branchId().equals(setup.branchId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("The active branch is not assigned to this user."));
        return new AnalyticsContextResponse(setup, active, accessible, List.of(active.branchId()));
    }

    public AnalyticsContextResponse reportContext(OwnerPrincipal principal,
                                                  AnalyticsReportType type,
                                                  Long requestedBranchId,
                                                  boolean allBranches) {
        AnalyticsContextResponse overview = overviewContext(principal);
        List<BranchAccessResponse> accessible = overview.accessibleBranches();
        if (allBranches) {
            boolean branchExpiry = type == AnalyticsReportType.BRANCH_WISE_EXPIRY;
            boolean consolidatedCustomerStatement = type == AnalyticsReportType.CUSTOMER_STATEMENT && principal.isOwner();
            if (!branchExpiry && !consolidatedCustomerStatement) {
                throw new AccessDeniedException("All-branch scope is not available for this report.");
            }
            return new AnalyticsContextResponse(overview.setup(), overview.reportBranch(), accessible,
                    accessible.stream().map(BranchAccessResponse::branchId).toList());
        }
        if (requestedBranchId == null || requestedBranchId.equals(overview.setup().branchId())) return overview;
        BranchAccessResponse selected = accessible.stream()
                .filter(branch -> branch.branchId().equals(requestedBranchId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You do not have access to the selected report branch."));
        return new AnalyticsContextResponse(overview.setup(), selected, accessible, List.of(selected.branchId()));
    }

    private List<BranchAccessResponse> accessibleBranches(OwnerPrincipal principal, Long businessId) {
        List<BranchAccessResponse> branches = principal.isOwner()
                ? branchContextService.findOwnerSelectableBranches(principal.ownerId())
                : branchContextService.findAssignedSelectableBranches(businessId, principal.staffUserId());
        if (branches.isEmpty()) throw new AccessDeniedException("No active branch is assigned to this user.");
        return branches;
    }

    private void requireAnalyticsAccess(OwnerPrincipal principal) {
        if (principal == null || !principal.hasPermission(SystemPermissionCode.ANALYTICS_REPORTS_ACCESS.name())) {
            throw new AccessDeniedException("Analytics and Reports access is required.");
        }
    }
}
