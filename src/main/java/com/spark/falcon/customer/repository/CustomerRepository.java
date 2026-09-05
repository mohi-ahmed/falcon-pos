package com.spark.falcon.customer.repository;

import com.spark.falcon.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Customer> findFirstByBusinessIdAndSystemControlledTrue(Long businessId);

    List<Customer> findByBusinessIdAndIdIn(Long businessId, Collection<Long> ids);

    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and (:archived is null
                   or (:archived = true and c.archivedAt is not null)
                   or (:archived = false and c.archivedAt is null))
            """)
    Page<Customer> findAllForBusiness(@Param("businessId") Long businessId,
                                      @Param("archived") Boolean archived,
                                      Pageable pageable);

    Page<Customer> findByBusinessIdAndActiveTrueAndArchivedAtIsNull(Long businessId, Pageable pageable);

    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and (:archived is null
                   or (:archived = true and c.archivedAt is not null)
                   or (:archived = false and c.archivedAt is null))
              and (
                    :keyword = ''
                    or (:customerId is not null and c.id = :customerId)
                    or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.phone, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%'))
                    or (:normalizedPhone is not null and coalesce(c.normalizedPhone, '') like concat('%', :normalizedPhone, '%'))
                    or lower(coalesce(c.normalizedEmail, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Customer> search(@Param("businessId") Long businessId,
                          @Param("archived") Boolean archived,
                          @Param("keyword") String keyword,
                          @Param("normalizedPhone") String normalizedPhone,
                          @Param("customerId") Long customerId,
                          Pageable pageable);

    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and c.active = true
              and c.archivedAt is null
              and (
                    :keyword = ''
                    or (:customerId is not null and c.id = :customerId)
                    or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.phone, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%'))
                    or (:normalizedPhone is not null and coalesce(c.normalizedPhone, '') like concat('%', :normalizedPhone, '%'))
                    or lower(coalesce(c.normalizedEmail, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Customer> searchActive(@Param("businessId") Long businessId,
                                @Param("keyword") String keyword,
                                @Param("normalizedPhone") String normalizedPhone,
                                @Param("customerId") Long customerId,
                                Pageable pageable);

    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and (:excludeId is null or c.id <> :excludeId)
              and (
                    (:normalizedPhone is not null and c.normalizedPhone = :normalizedPhone)
                    or (:normalizedEmail is not null and c.normalizedEmail = :normalizedEmail)
                  )
            order by c.archivedAt asc, c.id asc
            """)
    List<Customer> findProbableDuplicates(@Param("businessId") Long businessId,
                                          @Param("normalizedPhone") String normalizedPhone,
                                          @Param("normalizedEmail") String normalizedEmail,
                                          @Param("excludeId") Long excludeId,
                                          Pageable pageable);
}
