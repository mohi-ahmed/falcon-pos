package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.enumtype.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByBusinessIdAndEmailIgnoreCase(Long businessId, String email);

    Optional<User> findByBusinessIdAndEmailIgnoreCaseAndStatusAndArchivedAtIsNull(
            Long businessId, String email, UserStatus status);

    boolean existsByBusinessIdAndEmailIgnoreCase(Long businessId, String email);

    boolean existsByBusinessIdAndEmailIgnoreCaseAndIdNot(Long businessId, String email, Long id);

    boolean existsByUserGroupIdAndArchivedAtIsNull(Long userGroupId);

    long countByUserGroupId(Long userGroupId);

    long countByUserGroupIdAndArchivedAtIsNull(Long userGroupId);

    @Query("""
            select u from User u
            where u.businessId = :businessId
              and u.archivedAt is null
              and exists (
                    select ub.id from UserBranch ub
                    where ub.userId = u.id
                      and ub.branchId = :branchId
                      and ub.active = true
              )
            """)
    Page<User> findActiveListByBranch(
            @Param("businessId") Long businessId,
            @Param("branchId") Long branchId,
            Pageable pageable);

    @Query("""
            select u from User u
            where u.businessId = :businessId
              and u.archivedAt is null
              and exists (
                    select ub.id from UserBranch ub
                    where ub.userId = u.id
                      and ub.branchId = :branchId
                      and ub.active = true
              )
              and (
                    lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(u.email) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(u.mobileNumber, '')) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<User> searchActiveListByBranch(
            @Param("businessId") Long businessId,
            @Param("branchId") Long branchId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
