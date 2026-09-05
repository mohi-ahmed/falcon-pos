package com.spark.falcon.user.service;

import com.spark.falcon.user.dto.response.UserAccessResponse;
import com.spark.falcon.user.entity.Permission;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.UserGroup;
import com.spark.falcon.user.entity.UserBranch;
import com.spark.falcon.user.entity.enumtype.UserStatus;
import com.spark.falcon.user.repository.PermissionRepository;
import com.spark.falcon.user.repository.UserBranchRepository;
import com.spark.falcon.user.repository.UserGroupRepository;
import com.spark.falcon.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAccessServiceImpl implements UserAccessService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserBranchRepository userBranchRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccessResponse> findActiveByBusinessIdAndUserId(Long businessId, Long userId) {
        return userRepository.findByIdAndBusinessId(userId, businessId)
                .filter(User::isOperationallyActive)
                .map(this::toAccessResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccessResponse> findActiveByBusinessIdAndEmail(Long businessId, String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return userRepository.findByBusinessIdAndEmailIgnoreCaseAndStatusAndArchivedAtIsNull(
                        businessId, email.trim().toLowerCase(Locale.ROOT), UserStatus.ACTIVE)
                .map(this::toAccessResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBranchAccess(Long businessId, Long userId, Long branchId) {
        return findActiveByBusinessIdAndUserId(businessId, userId)
                .map(access -> access.branchIds().contains(branchId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(Long businessId, Long userId, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) return false;
        String normalized = permissionCode.trim().toUpperCase(Locale.ROOT);
        return findActiveByBusinessIdAndUserId(businessId, userId)
                .map(access -> access.permissionCodes().contains(normalized))
                .orElse(false);
    }

    private UserAccessResponse toAccessResponse(User user) {
        UserGroup group = userGroupRepository.findByIdAndBusinessId(user.getUserGroupId(), user.getBusinessId())
                .filter(value -> !value.isArchived())
                .orElse(null);
        if (group == null) {
            return new UserAccessResponse(user.getId(), user.getBusinessId(), user.getEmail(), user.getUserGroupId(),
                    null, user.getStatus(), Set.of(), Set.of());
        }

        LinkedHashSet<Long> branchIds = new LinkedHashSet<>();
        userBranchRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(UserBranch::getBranchId)
                .forEach(branchIds::add);

        LinkedHashSet<String> permissionCodes = new LinkedHashSet<>();
        permissionRepository.findActiveForUserGroup(group.getId()).stream()
                .map(Permission::getCode)
                .forEach(permissionCodes::add);

        return new UserAccessResponse(user.getId(), user.getBusinessId(), user.getEmail(), group.getId(),
                group.getName(), user.getStatus(), Set.copyOf(branchIds), Set.copyOf(permissionCodes));
    }
}
