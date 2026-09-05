package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.Unit;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    boolean existsByBusinessIdAndCodeIgnoreCase(Long businessId, String code);
    boolean existsByBusinessIdAndCodeIgnoreCaseAndIdNot(Long businessId, String code, Long id);
    Optional<Unit> findByIdAndBusinessId(Long id, Long businessId);
    Optional<Unit> findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(
            Long id, Long businessId, ConfigurationStatus status);
    List<Unit> findByBusinessIdAndIdInAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
            Long businessId, Collection<Long> ids, ConfigurationStatus status);
    List<Unit> findByBusinessIdAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(Long businessId);
}
