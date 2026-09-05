package com.spark.falcon.settings.controller;

import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class UiBrandingControllerAdvice {
    private final BranchContextService branchContextService;
    private final BranchSettingsAccessService branchSettingsAccessService;

    @ModelAttribute("uiBrandingSettings")
    public BranchSettingsResponse uiBrandingSettings(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OwnerPrincipal principal)) {
            return null;
        }
        try {
            var setup = branchContextService.resolveOwnerSetup(principal.ownerId());
            if (setup.businessId() == null || setup.branchId() == null) return null;
            return branchSettingsAccessService
                    .findByBusinessIdAndBranchId(setup.businessId(), setup.branchId())
                    .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
