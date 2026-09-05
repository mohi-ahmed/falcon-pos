package com.spark.falcon.identity.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityLogoutAuditListener {

    private final SecurityAuditService securityAuditService;
    private final RequestAuditMetadataProvider requestAuditMetadataProvider;

    @EventListener
    public void onLogoutSuccess(
            LogoutSuccessEvent event
    ) {

        RequestAuditMetadataProvider.AuditRequestMetadata metadata =
                requestAuditMetadataProvider.current();

        securityAuditService.record(
                SecurityAuditEventType.LOGOUT,
                event.getAuthentication() != null
                        ? event.getAuthentication().getName()
                        : null,
                metadata.ipAddress(),
                metadata.userAgent(),
                "Management session signed out"
        );
    }
}