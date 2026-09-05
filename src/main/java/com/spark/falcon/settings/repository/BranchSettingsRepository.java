package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.BranchSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchSettingsRepository extends JpaRepository<BranchSettings, Long> {
    Optional<BranchSettings> findByBusinessIdAndBranchId(Long businessId, Long branchId);
}
