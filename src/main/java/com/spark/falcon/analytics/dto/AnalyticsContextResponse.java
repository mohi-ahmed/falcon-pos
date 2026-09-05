package com.spark.falcon.analytics.dto;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;

import java.util.List;

public record AnalyticsContextResponse(
        BusinessSetupResponse setup,
        BranchAccessResponse reportBranch,
        List<BranchAccessResponse> accessibleBranches,
        List<Long> reportBranchIds
) { }
