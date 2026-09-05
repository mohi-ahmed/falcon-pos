package com.spark.falcon.identity.security;

import com.spark.falcon.user.service.UserAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class StaffAuthenticationRefreshFilter extends OncePerRequestFilter {

    private final UserAccessService userAccessService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerPrincipal principal && principal.isStaff()) {
            var access = userAccessService.findActiveByBusinessIdAndUserId(principal.businessId(), principal.staffUserId())
                    .orElse(null);
            if (access == null || access.branchIds().isEmpty()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
            OwnerPrincipal refreshedPrincipal = principal.refreshStaff(access);
            UsernamePasswordAuthenticationToken refreshed = new UsernamePasswordAuthenticationToken(
                    refreshedPrincipal, null, refreshedPrincipal.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshed);
        }
        filterChain.doFilter(request, response);
    }
}
