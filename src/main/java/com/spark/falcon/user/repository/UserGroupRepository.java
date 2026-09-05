package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, Long> {
    Optional<UserGroup> findByIdAndBusinessId(Long id, Long businessId);
    boolean existsByBusinessIdAndSlugIgnoreCase(Long businessId, String slug);
    boolean existsByBusinessIdAndSlugIgnoreCaseAndIdNot(Long businessId, String slug, Long id);
    List<UserGroup> findByBusinessIdAndArchivedAtIsNullOrderByNameAsc(Long businessId);
}
