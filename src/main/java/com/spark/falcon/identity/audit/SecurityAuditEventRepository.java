package com.spark.falcon.identity.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository
        extends JpaRepository<SecurityAuditEvent, Long> {
}