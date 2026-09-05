package com.spark.falcon.user.mapper;

import com.spark.falcon.user.dto.command.*;
import com.spark.falcon.user.dto.request.*;
import com.spark.falcon.user.dto.response.*;
import com.spark.falcon.user.entity.Permission;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.UserGroup;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class UserManagementMapper {

    public CreateUserCommand toCreateCommand(ManagementActor actor, CreateUserRequest request) {
        return new CreateUserCommand(actor, request.getFullName(), request.getEmail(), request.getMobileNumber(),
                request.getPassword(), request.getPasswordConfirmation(), request.getDateOfBirth(),
                request.getProfilePhotoReference(), request.getUserGroupId(), copy(request.getBranchIds()), request.getStatus());
    }

    public UpdateUserCommand toUpdateCommand(ManagementActor actor, Long userId, UpdateUserRequest request) {
        return new UpdateUserCommand(actor, userId, request.getFullName(), request.getEmail(), request.getMobileNumber(),
                request.getDateOfBirth(), request.getProfilePhotoReference(), request.getUserGroupId(),
                copy(request.getBranchIds()), request.getStatus());
    }

    public CreateUserGroupCommand toCreateCommand(ManagementActor actor, UserGroupRequest request) {
        return new CreateUserGroupCommand(actor, request.getName(), request.getSlug());
    }

    public UpdateUserGroupCommand toUpdateCommand(ManagementActor actor, Long groupId, UserGroupRequest request) {
        return new UpdateUserGroupCommand(actor, groupId, request.getName(), request.getSlug());
    }

    public ConfigureGroupPermissionsCommand toPermissionCommand(
            ManagementActor actor, Long groupId, GroupPermissionRequest request) {
        return new ConfigureGroupPermissionsCommand(actor, groupId, copy(request.getPermissionIds()));
    }

    public UserResponse toResponse(User user, UserGroup group, Set<Long> branchIds) {
        return new UserResponse(user.getId(), user.getBusinessId(), user.getFullName(), user.getEmail(),
                user.getMobileNumber(), user.getDateOfBirth(), user.getProfilePhotoReference(), user.getUserGroupId(),
                group.getName(), user.getStatus(), Set.copyOf(branchIds), user.getCreatedAt(), user.isArchived());
    }

    public UserGroupResponse toResponse(UserGroup group, long userCount, Set<Long> permissionIds) {
        return new UserGroupResponse(group.getId(), group.getBusinessId(), group.getName(), group.getSlug(), userCount,
                Set.copyOf(permissionIds), group.isProtectedGroup(), group.isArchived(), group.getCreatedAt());
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName());
    }

    private static Set<Long> copy(Set<Long> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
