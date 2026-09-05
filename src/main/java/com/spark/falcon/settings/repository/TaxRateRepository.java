package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.TaxRate;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    boolean existsByBusinessIdAndCodeIgnoreCase(Long businessId, String code);
    boolean existsByBusinessIdAndCodeIgnoreCaseAndIdNot(Long businessId, String code, Long id);
    Optional<TaxRate> findByIdAndBusinessId(Long id, Long businessId);
    Optional<TaxRate> findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(
            Long id, Long businessId, ConfigurationStatus status);
    List<TaxRate> findByBusinessIdAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
            Long businessId, ConfigurationStatus status);
    List<TaxRate> findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(Long businessId);
}
