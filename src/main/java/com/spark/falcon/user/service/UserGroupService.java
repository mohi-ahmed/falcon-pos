package com.spark.falcon.user.service;

import com.spark.falcon.user.dto.command.ConfigureGroupPermissionsCommand;
import com.spark.falcon.user.dto.command.CreateUserGroupCommand;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.dto.command.UpdateUserGroupCommand;
import com.spark.falcon.user.dto.response.UserGroupResponse;
import com.spark.falcon.user.entity.GroupPermission;
import com.spark.falcon.user.entity.Permission;
import com.spark.falcon.user.entity.UserGroup;
import com.spark.falcon.user.entity.UserManagementAuditEvent;
import com.spark.falcon.user.entity.enumtype.ManagementActorType;
import com.spark.falcon.user.entity.enumtype.SystemPermissionCode;
import com.spark.falcon.user.entity.enumtype.UserManagementAuditAction;
import com.spark.falcon.user.exception.PermissionNotFoundException;
import com.spark.falcon.user.exception.ProtectedUserGroupException;
import com.spark.falcon.user.exception.UserGroupInUseException;
import com.spark.falcon.user.exception.UserGroupNotFoundException;
import com.spark.falcon.user.exception.UserGroupSlugAlreadyUsedException;
import com.spark.falcon.user.exception.UserManagementAccessDeniedException;
import com.spark.falcon.user.mapper.UserManagementMapper;
import com.spark.falcon.user.repository.GroupPermissionRepository;
import com.spark.falcon.user.repository.PermissionRepository;
import com.spark.falcon.user.repository.UserGroupRepository;
import com.spark.falcon.user.repository.UserManagementAuditEventRepository;
import com.spark.falcon.user.repository.UserRepository;
import com.spark.falcon.user.validation.UserManagementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final UserManagementAuditEventRepository auditRepository;
    private final UserAccessService userAccessService;
    private final UserManagementValidator validator;
    private final UserManagementMapper mapper;
    private final Clock clock;

    @Transactional
    public UserGroupResponse create(CreateUserGroupCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        ManagementActor actor = requiredActor(command.actor());
        String slug = validator.normalizeSlug(command.name(), command.slug());
        if (userGroupRepository.existsByBusinessIdAndSlugIgnoreCase(actor.businessId(), slug)) {
            throw new UserGroupSlugAlreadyUsedException(slug);
        }

        Instant now = Instant.now(clock);
        UserGroup group = UserGroup.create(actor.businessId(), command.name(), slug, now);
        userGroupRepository.saveAndFlush(group);
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_GROUP_CREATED, "USER_GROUP", group.getId(), now));
        auditRepository.flush();
        return mapper.toResponse(group, 0, Set.of());
    }

    @Transactional
    public UserGroupResponse update(UpdateUserGroupCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        ManagementActor actor = requiredActor(command.actor());
        UserGroup group = activeGroup(actor.businessId(), command.userGroupId());
        rejectProtected(group);

        String slug = validator.normalizeSlug(command.name(), command.slug());
        if (userGroupRepository.existsByBusinessIdAndSlugIgnoreCaseAndIdNot(actor.businessId(), slug, group.getId())) {
            throw new UserGroupSlugAlreadyUsedException(slug);
        }

        Instant now = Instant.now(clock);
        try {
            group.update(command.name(), slug, now);
        } catch (IllegalStateException exception) {
            throw new ProtectedUserGroupException();
        }
        userGroupRepository.flush();
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_GROUP_UPDATED, "USER_GROUP", group.getId(), now));
        auditRepository.flush();
        return toResponse(group);
    }

    @Transactional
    public UserGroupResponse configurePermissions(ConfigureGroupPermissionsCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        ManagementActor actor = requiredActor(command.actor());
        UserGroup group = activeGroup(actor.businessId(), command.userGroupId());
        rejectProtected(group);

        Set<Long> requestedIds = command.permissionIds() == null
                ? Set.of()
                : new LinkedHashSet<>(command.permissionIds());
        if (requestedIds.stream().anyMatch(id -> id == null || id <= 0)) throw new PermissionNotFoundException();

        Map<Long, Permission> permissions = permissionRepository.findAllById(requestedIds).stream()
                .filter(Permission::isActive)
                .collect(Collectors.toMap(Permission::getId, permission -> permission));
        if (permissions.size() != requestedIds.size()) throw new PermissionNotFoundException();

        Instant now = Instant.now(clock);
        try {
            group.ensurePermissionsEditable();
        } catch (IllegalStateException exception) {
            throw new ProtectedUserGroupException();
        }

        List<GroupPermission> existing = groupPermissionRepository.findByUserGroupId(group.getId());
        Map<Long, GroupPermission> byPermissionId = existing.stream()
                .collect(Collectors.toMap(GroupPermission::getPermissionId, assignment -> assignment,
                        (left, right) -> left, LinkedHashMap::new));

        for (GroupPermission assignment : existing) {
            if (requestedIds.contains(assignment.getPermissionId())) assignment.activate(now);
            else assignment.deactivate(now);
        }
        for (Long permissionId : requestedIds) {
            if (!byPermissionId.containsKey(permissionId)) {
                groupPermissionRepository.save(GroupPermission.assign(group.getId(), permissionId, now));
            }
        }
        groupPermissionRepository.flush();
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_GROUP_PERMISSIONS_UPDATED, "USER_GROUP", group.getId(), now));
        auditRepository.flush();
        return toResponse(group);
    }

    @Transactional
    public void archive(ManagementActor actor, Long groupId) {
        actor = requiredActor(actor);
        UserGroup group = activeGroup(actor.businessId(), groupId);
        rejectProtected(group);
        if (userRepository.existsByUserGroupIdAndArchivedAtIsNull(group.getId())) throw new UserGroupInUseException();

        Instant now = Instant.now(clock);
        try {
            group.archive(now);
        } catch (IllegalStateException exception) {
            throw new ProtectedUserGroupException();
        }
        for (GroupPermission assignment : groupPermissionRepository.findByUserGroupId(group.getId())) {
            if (assignment.isActive()) assignment.deactivate(now);
        }
        groupPermissionRepository.flush();
        userGroupRepository.flush();
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_GROUP_ARCHIVED, "USER_GROUP", group.getId(), now));
        auditRepository.flush();
    }

    @Transactional(readOnly = true)
    public UserGroupResponse findById(ManagementActor actor, Long groupId) {
        actor = requiredActor(actor);
        return toResponse(activeGroup(actor.businessId(), groupId));
    }

    @Transactional(readOnly = true)
    public List<UserGroupResponse> findAll(ManagementActor actor) {
        actor = requiredActor(actor);
        return userGroupRepository.findByBusinessIdAndArchivedAtIsNullOrderByNameAsc(actor.businessId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserGroupResponse toResponse(UserGroup group) {
        Set<Long> permissionIds = groupPermissionRepository.findByUserGroupIdAndActiveTrue(group.getId()).stream()
                .map(GroupPermission::getPermissionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return mapper.toResponse(group, userRepository.countByUserGroupIdAndArchivedAtIsNull(group.getId()), permissionIds);
    }

    private UserGroup activeGroup(Long businessId, Long groupId) {
        if (groupId == null || groupId <= 0) throw new UserGroupNotFoundException();
        return userGroupRepository.findByIdAndBusinessId(groupId, businessId)
                .filter(value -> !value.isArchived())
                .orElseThrow(UserGroupNotFoundException::new);
    }

    private void rejectProtected(UserGroup group) {
        if (group.isProtectedGroup()) throw new ProtectedUserGroupException();
    }

    private ManagementActor requiredActor(ManagementActor actor) {
        if (actor == null || actor.businessId() == null || actor.businessId() <= 0
                || actor.type() == null || actor.actorId() == null || actor.actorId() <= 0) {
            throw new UserManagementAccessDeniedException();
        }
        if (actor.type() == ManagementActorType.USER
                && !userAccessService.hasPermission(actor.businessId(), actor.actorId(),
                SystemPermissionCode.USER_MANAGEMENT.name())) {
            throw new UserManagementAccessDeniedException();
        }
        return actor;
    }
}
