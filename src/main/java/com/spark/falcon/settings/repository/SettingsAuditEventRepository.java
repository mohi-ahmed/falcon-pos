package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.SettingsAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsAuditEventRepository extends JpaRepository<SettingsAuditEvent, Long> {
}
