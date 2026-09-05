package com.spark.falcon.businesssetup.controller;

import com.spark.falcon.branch.exception.BranchBusinessAccessDeniedException;
import com.spark.falcon.businesssetup.service.BusinessSetupService;
import com.spark.falcon.dashboard.dto.OwnerDashboardResponse;
import com.spark.falcon.dashboard.service.OwnerDashboardService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class OwnerDashboardEntryController {

    private final BusinessSetupService setupService;
    private final OwnerDashboardService dashboardService;

    @GetMapping("/owner/dashboard")
    String dashboard(@RequestParam(required = false) String period,
                     @RequestParam(required = false) String month,
                     @AuthenticationPrincipal OwnerPrincipal principal,
                     Model model) {
        if (principal == null) return "redirect:/login";
        if (setupService.findByOwner(principal.ownerId()).isEmpty()) {
            return principal.isOwner() ? "redirect:/owner/setup/business" : "redirect:/login?error";
        }
        try {
            OwnerDashboardResponse dashboard = dashboardService.load(principal, period, month);
            model.addAttribute("setup", dashboard.context().setup());
            model.addAttribute("dashboard", dashboard);
            model.addAttribute("accessibleBranches", dashboard.context().accessibleBranches());
            return "onboarding/dashboard-entry";
        } catch (BranchBusinessAccessDeniedException exception) {
            return principal.isOwner() ? "redirect:/owner/branches" : "redirect:/owner/branches/change";
        }
    }
}
