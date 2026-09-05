package com.spark.falcon.identity.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityAuthenticationAuditListener {

    private final SecurityAuditService securityAuditService;
    private final RequestAuditMetadataProvider requestAuditMetadataProvider;

    @EventListener
    public void onAuthenticationSuccess(
            AuthenticationSuccessEvent event
    ) {

        RequestAuditMetadataProvider.AuditRequestMetadata metadata =
                requestAuditMetadataProvider.current();

        securityAuditService.record(
                SecurityAuditEventType.LOGIN_SUCCESS,
                event.getAuthentication().getName(),
                metadata.ipAddress(),
                metadata.userAgent(),
                "Management authentication succeeded"
        );
    }

    @EventListener
    public void onAuthenticationFailure(
            AuthenticationFailureBadCredentialsEvent event
    ) {

        RequestAuditMetadataProvider.AuditRequestMetadata metadata =
                requestAuditMetadataProvider.current();

        securityAuditService.record(
                SecurityAuditEventType.LOGIN_FAILURE,
                event.getAuthentication().getName(),
                metadata.ipAddress(),
                metadata.userAgent(),
                "Management authentication failed"
        );
    }
}