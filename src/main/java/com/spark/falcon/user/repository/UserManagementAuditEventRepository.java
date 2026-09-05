package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.UserManagementAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserManagementAuditEventRepository extends JpaRepository<UserManagementAuditEvent, Long> {
}
