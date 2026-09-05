package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    boolean existsByBusinessIdAndCodeIgnoreCase(Long businessId, String code);
    boolean existsByBusinessIdAndCodeIgnoreCaseAndIdNot(Long businessId, String code, Long id);
    Optional<PaymentMethod> findByIdAndBusinessId(Long id, Long businessId);
    Optional<PaymentMethod> findByIdAndBusinessIdAndStatusAndArchivedAtIsNull(
            Long id, Long businessId, ConfigurationStatus status);
    List<PaymentMethod> findByBusinessIdAndIdInAndStatusAndArchivedAtIsNullOrderByDisplayOrderAscNameAsc(
            Long businessId, Collection<Long> ids, ConfigurationStatus status);
    /**
     * Management list: includes both ACTIVE and INACTIVE methods while excluding only archived records.
     * POS/payment selection uses the separate active-only access queries above.
     */
    @Query("""
            select pm
            from PaymentMethod pm
            where pm.businessId = :businessId
              and pm.archivedAt is null
            order by pm.displayOrder asc, pm.name asc
            """)
    List<PaymentMethod> findManageableByBusinessId(@Param("businessId") Long businessId);
}
