package com.spark.falcon.branch.service;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
@Getter
public class ActiveBranchSession {
    private Long businessId;
    private Long branchId;

    public void select(Long businessId, Long branchId) {
        this.businessId = businessId;
        this.branchId = branchId;
    }

    public void clear() {
        businessId = null;
        branchId = null;
    }

    public boolean isSelected(Long expectedBusinessId, Long expectedBranchId) {
        return expectedBusinessId != null && expectedBusinessId.equals(businessId)
                && expectedBranchId != null && expectedBranchId.equals(branchId);
    }
}
