package com.spark.falcon.identity.security;

import com.spark.falcon.branch.service.ActiveBranchSession;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.user.service.UserAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StaffBranchAccessInterceptor implements HandlerInterceptor {

    private final UserAccessService userAccessService;
    private final BranchContextService branchContextService;
    private final ActiveBranchSession activeBranchSession;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object raw = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(raw instanceof OwnerPrincipal principal) || !principal.isStaff()) return true;

        ensureActiveBranch(principal);
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (!"/owner/branches/change".equals(path)) {
            String branchIdValue = request.getParameter("branchId");
            if (branchIdValue != null && !branchIdValue.isBlank()) {
                Long requested = parse(branchIdValue);
                if (requested == null || !requested.equals(activeBranchSession.getBranchId())) throw denied();
            }
        }

        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map && map.get("branchId") != null) {
            Long requested = parse(map.get("branchId").toString());
            if (requested == null || !userAccessService.hasActiveBranchAccess(
                    principal.businessId(), principal.staffUserId(), requested)) throw denied();
        }
        return true;
    }

    private void ensureActiveBranch(OwnerPrincipal principal) {
        Long selected = activeBranchSession.getBranchId();
        if (selected != null && principal.businessId().equals(activeBranchSession.getBusinessId())
                && userAccessService.hasActiveBranchAccess(principal.businessId(), principal.staffUserId(), selected)) {
            return;
        }
        var branches = branchContextService.findAssignedSelectableBranches(principal.businessId(), principal.staffUserId());
        if (branches.isEmpty()) throw denied();
        branchContextService.selectAssignedBranch(principal.businessId(), principal.staffUserId(), branches.getFirst().branchId());
    }

    private Long parse(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException exception) { return null; }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("The requested branch is not assigned to this staff account");
    }
}
