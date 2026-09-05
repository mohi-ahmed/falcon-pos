package com.spark.falcon.identity.service;

import com.spark.falcon.identity.audit.RequestAuditMetadataProvider;
import com.spark.falcon.identity.audit.SecurityAuditEventType;
import com.spark.falcon.identity.audit.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetAuditRecorder {
    private final SecurityAuditService auditService;
    private final RequestAuditMetadataProvider metadataProvider;

    public void record(SecurityAuditEventType type, String actor, String details) {
        RequestAuditMetadataProvider.AuditRequestMetadata metadata = metadataProvider.current();
        auditService.record(type, actor, metadata.ipAddress(), metadata.userAgent(), details);
    }

    public void recordAfterCommit(SecurityAuditEventType type, String actor, String details) {
        RequestAuditMetadataProvider.AuditRequestMetadata metadata = metadataProvider.current();
        auditService.recordAfterCommit(type, actor, metadata.ipAddress(), metadata.userAgent(), details);
    }
}
