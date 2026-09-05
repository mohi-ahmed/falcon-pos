package com.spark.falcon.dashboard.dto;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;

import java.util.List;

public record DashboardContextResponse(
        BusinessSetupResponse setup,
        BranchAccessResponse activeBranch,
        List<BranchAccessResponse> accessibleBranches,
        String currentUser,
        String currentRole
) { }
