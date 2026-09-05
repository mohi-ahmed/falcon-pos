package com.spark.falcon.identity.security;

import com.spark.falcon.branch.service.BranchContextService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FalconAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final BranchContextService branchContextService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OwnerPrincipal principal = (OwnerPrincipal) authentication.getPrincipal();
        if (principal.isOwner()) {
            response.sendRedirect(request.getContextPath() + "/owner/setup/business");
            return;
        }

        var branches = branchContextService.findAssignedSelectableBranches(principal.businessId(), principal.staffUserId());
        if (branches.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        branchContextService.selectAssignedBranch(principal.businessId(), principal.staffUserId(), branches.getFirst().branchId());
        response.sendRedirect(request.getContextPath() + firstPermittedPath(principal));
    }

    private String firstPermittedPath(OwnerPrincipal principal) {
        List<PermissionLanding> landings = List.of(
                new PermissionLanding("POS_OPEN", "/owner/pos"),
                new PermissionLanding("SELL_ACCESS", "/owner/sales"),
                new PermissionLanding("PURCHASE_ACCESS", "/owner/purchases"),
                new PermissionLanding("PRODUCT_ACCESS", "/owner/products"),
                new PermissionLanding("SUPPLIER_ACCESS", "/owner/suppliers"),
                new PermissionLanding("CUSTOMER_ACCESS", "/owner/customers"),
                new PermissionLanding("PAYMENT_VIEW", "/owner/payments"),
                new PermissionLanding("PAYMENT_VIEW_CONSOLIDATED", "/owner/payments"),
                new PermissionLanding("PAYMENT_RECEIVE_CUSTOMER_DUE", "/owner/payments/customer"),
                new PermissionLanding("PAYMENT_PAY_SUPPLIER_DUE", "/owner/payments/supplier"),
                new PermissionLanding("EXPENDITURE_ACCESS", "/owner/expenses"),
                new PermissionLanding("EXPENSE_VIEW", "/owner/expenses"),
                new PermissionLanding("INVENTORY_VIEW", "/owner/inventory"),
                new PermissionLanding("CASH_MANAGEMENT_ACCESS", "/owner/cash-management"),
                new PermissionLanding("USER_MANAGEMENT", "/owner/users"),
                new PermissionLanding("SETTINGS_ACCESS", "/owner/settings/general"),
                new PermissionLanding("BRANCH_MANAGEMENT", "/owner/branches"),
                new PermissionLanding("BRANCH_CHANGE", "/owner/branches/change"),
                new PermissionLanding("ANALYTICS_REPORTS_ACCESS", "/owner/analytics")
        );
        return landings.stream().filter(value -> principal.hasPermission(value.permissionCode()))
                .map(PermissionLanding::path).findFirst().orElse("/");
    }

    private record PermissionLanding(String permissionCode, String path) {}
}
