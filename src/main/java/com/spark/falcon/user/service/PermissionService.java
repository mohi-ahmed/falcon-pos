package com.spark.falcon.user.service;

import com.spark.falcon.user.dto.response.PermissionResponse;
import com.spark.falcon.user.entity.Permission;
import com.spark.falcon.user.entity.enumtype.SystemPermissionCode;
import com.spark.falcon.user.mapper.UserManagementMapper;
import com.spark.falcon.user.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserManagementMapper mapper;
    private final Clock clock;

    @Transactional
    public void ensureDocumentedPermissions() {
        Instant now = Instant.now(clock);
        for (SystemPermissionCode definition : SystemPermissionCode.values()) {
            if (permissionRepository.findByCodeIgnoreCase(definition.name()).isEmpty()) {
                permissionRepository.save(Permission.create(definition.name(), definition.displayName(), now));
            }
        }
        permissionRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAllActive() {
        return permissionRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(mapper::toResponse)
                .toList();
    }
}
