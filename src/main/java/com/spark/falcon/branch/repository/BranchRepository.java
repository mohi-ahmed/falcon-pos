package com.spark.falcon.branch.repository;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    boolean existsByBusinessIdAndCodeIgnoreCase(Long businessId, String code);
    boolean existsByBusinessIdAndCodeIgnoreCaseAndIdNot(Long businessId, String code, Long id);
    Optional<Branch> findByBusinessIdAndCreateIdempotencyKey(Long businessId, String idempotencyKey);
    Optional<Branch> findFirstByBusinessIdOrderByIdAsc(Long businessId);
    Optional<Branch> findByIdAndBusinessId(Long id, Long businessId);
    Optional<Branch> findByIdAndBusinessIdAndStatus(Long id, Long businessId, BranchStatus status);
    List<Branch> findByBusinessIdAndStatusOrderByNameAsc(Long businessId, BranchStatus status);
    Page<Branch> findByBusinessId(Long businessId, Pageable pageable);
    Page<Branch> findByBusinessIdAndIdIn(Long businessId, java.util.Collection<Long> ids, Pageable pageable);

    @Query("""
            select b from Branch b
            where b.businessId = :businessId
              and (lower(b.name) like lower(concat('%', :keyword, '%'))
                or lower(b.code) like lower(concat('%', :keyword, '%'))
                or lower(b.country) like lower(concat('%', :keyword, '%'))
                or lower(b.address) like lower(concat('%', :keyword, '%')))
            """)
    Page<Branch> searchByBusinessId(@Param("businessId") Long businessId,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);

    @Query("""
            select b from Branch b
            where b.businessId = :businessId
              and b.id in :ids
              and (lower(b.name) like lower(concat('%', :keyword, '%'))
                or lower(b.code) like lower(concat('%', :keyword, '%'))
                or lower(b.country) like lower(concat('%', :keyword, '%'))
                or lower(b.address) like lower(concat('%', :keyword, '%')))
            """)
    Page<Branch> searchByBusinessIdAndIdIn(@Param("businessId") Long businessId,
                                           @Param("ids") java.util.Collection<Long> ids,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}
