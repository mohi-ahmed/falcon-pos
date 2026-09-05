package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.GroupPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupPermissionRepository extends JpaRepository<GroupPermission, Long> {
    List<GroupPermission> findByUserGroupId(Long userGroupId);
    List<GroupPermission> findByUserGroupIdAndActiveTrue(Long userGroupId);
}
