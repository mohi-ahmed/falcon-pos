package com.spark.falcon.user.repository;

import com.spark.falcon.user.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCodeIgnoreCase(String code);
    List<Permission> findByActiveTrueOrderByCodeAsc();

    @Query("""
            select p from Permission p
            where p.active = true
              and exists (
                    select gp.id from GroupPermission gp
                    where gp.permissionId = p.id
                      and gp.userGroupId = :userGroupId
                      and gp.active = true
              )
            order by p.code asc
            """)
    List<Permission> findActiveForUserGroup(@Param("userGroupId") Long userGroupId);
}
