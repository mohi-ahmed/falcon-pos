package com.spark.falcon.user.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.user.dto.command.CreateUserCommand;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.dto.command.UpdateUserCommand;
import com.spark.falcon.user.dto.response.UserResponse;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.UserBranch;
import com.spark.falcon.user.entity.UserGroup;
import com.spark.falcon.user.entity.UserManagementAuditEvent;
import com.spark.falcon.user.entity.enumtype.ManagementActorType;
import com.spark.falcon.user.entity.enumtype.SystemPermissionCode;
import com.spark.falcon.user.entity.enumtype.UserManagementAuditAction;
import com.spark.falcon.user.exception.UserEmailAlreadyUsedException;
import com.spark.falcon.user.exception.UserGroupNotFoundException;
import com.spark.falcon.user.exception.UserManagementAccessDeniedException;
import com.spark.falcon.user.exception.UserNotFoundException;
import com.spark.falcon.user.mapper.UserManagementMapper;
import com.spark.falcon.user.repository.UserBranchRepository;
import com.spark.falcon.user.repository.UserGroupRepository;
import com.spark.falcon.user.repository.UserManagementAuditEventRepository;
import com.spark.falcon.user.repository.UserRepository;
import com.spark.falcon.user.validation.UserManagementValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserService {

    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserBranchRepository userBranchRepository;
    private final UserManagementAuditEventRepository auditRepository;
    private final BranchAccessService branchAccessService;
    private final UserAccessService userAccessService;
    private final UserManagementValidator validator;
    private final UserManagementMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public UserResponse create(CreateUserCommand command) {
        ManagementActor actor = requiredActor(command == null ? null : command.actor());
        validator.validateCreateUser(command);

        String email = validator.normalizeEmail(command.email());
        if (userRepository.existsByBusinessIdAndEmailIgnoreCase(actor.businessId(), email)) {
            throw new UserEmailAlreadyUsedException(email);
        }

        UserGroup group = activeGroup(actor.businessId(), command.userGroupId());
        Set<Long> branchIds = validateBranches(actor, command.branchIds());
        Instant now = Instant.now(clock);

        User user = User.create(actor.businessId(), group.getId(), command.fullName(), email, command.mobileNumber(),
                passwordEncoder.encode(command.rawPassword()), command.dateOfBirth(), command.profilePhotoReference(),
                command.status(), now);
        userRepository.saveAndFlush(user);
        syncBranchAssignments(user.getId(), branchIds, now);
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_CREATED, "USER", user.getId(), now));
        auditRepository.flush();

        return mapper.toResponse(user, group, branchIds);
    }

    @Transactional
    public UserResponse update(UpdateUserCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        ManagementActor actor = requiredActor(command.actor());
        if (command.userId() == null || command.userId() <= 0) throw new UserNotFoundException();
        validator.validateBranchIds(command.branchIds());

        User user = activeUser(actor.businessId(), command.userId());
        assertActorCanManageUser(actor, user.getId());
        String email = validator.normalizeEmail(command.email());
        if (userRepository.existsByBusinessIdAndEmailIgnoreCaseAndIdNot(actor.businessId(), email, user.getId())) {
            throw new UserEmailAlreadyUsedException(email);
        }

        UserGroup group = activeGroup(actor.businessId(), command.userGroupId());
        Set<Long> branchIds = validateBranches(actor, command.branchIds());
        Instant now = Instant.now(clock);

        user.update(group.getId(), command.fullName(), email, command.mobileNumber(), command.dateOfBirth(),
                command.profilePhotoReference(), command.status(), now);
        syncBranchAssignments(user.getId(), branchIds, now);
        userRepository.flush();
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_UPDATED, "USER", user.getId(), now));
        auditRepository.flush();

        return mapper.toResponse(user, group, branchIds);
    }

    @Transactional
    public void archive(ManagementActor actor, Long userId) {
        actor = requiredActor(actor);
        if (userId == null || userId <= 0) throw new UserNotFoundException();
        if (actor.type() == ManagementActorType.USER && actor.actorId().equals(userId)) {
            throw new UserManagementAccessDeniedException();
        }

        User user = activeUser(actor.businessId(), userId);
        assertActorCanManageUser(actor, user.getId());
        Instant now = Instant.now(clock);
        user.archive(now);
        for (UserBranch assignment : userBranchRepository.findByUserId(user.getId())) {
            if (assignment.isActive()) assignment.deactivate(now);
        }
        userBranchRepository.flush();
        userRepository.flush();
        auditRepository.save(UserManagementAuditEvent.record(actor.businessId(), actor.type(), actor.actorId(),
                UserManagementAuditAction.USER_ARCHIVED, "USER", user.getId(), now));
        auditRepository.flush();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(ManagementActor actor, Long userId) {
        actor = requiredActor(actor);
        User user = activeUser(actor.businessId(), userId);
        assertActorCanManageUser(actor, user.getId());
        UserGroup group = activeGroup(actor.businessId(), user.getUserGroupId());
        return mapper.toResponse(user, group, activeBranchIds(user.getId()));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findByBranch(ManagementActor actor, Long branchId, String keyword, Pageable pageable) {
        actor = requiredActor(actor);
        if (pageable == null) throw new IllegalArgumentException("pageable is required");
        assertBranchAccessible(actor, branchId);

        String normalizedKeyword = validator.normalizeKeyword(keyword);
        Page<User> page = normalizedKeyword == null
                ? userRepository.findActiveListByBranch(actor.businessId(), branchId, pageable)
                : userRepository.searchActiveListByBranch(actor.businessId(), branchId, normalizedKeyword, pageable);
        Map<Long, UserGroup> groups = loadGroups(actor.businessId(), page.getContent());
        Map<Long, Set<Long>> branchesByUser = loadBranchIds(page.getContent());
        return page.map(user -> {
            UserGroup group = groups.get(user.getUserGroupId());
            if (group == null) throw new UserGroupNotFoundException();
            return mapper.toResponse(user, group, branchesByUser.getOrDefault(user.getId(), Set.of()));
        });
    }


    private void assertActorCanManageUser(ManagementActor actor, Long targetUserId) {
        if (actor.type() == ManagementActorType.OWNER) return;
        Set<Long> actorBranches = userAccessService.findActiveByBusinessIdAndUserId(actor.businessId(), actor.actorId())
                .map(access -> access.branchIds())
                .orElseThrow(UserManagementAccessDeniedException::new);
        Set<Long> targetBranches = activeBranchIds(targetUserId);
        if (targetBranches.stream().noneMatch(actorBranches::contains)) {
            throw new UserManagementAccessDeniedException();
        }
    }

    private Set<Long> validateBranches(ManagementActor actor, Set<Long> requestedBranchIds) {
        validator.validateBranchIds(requestedBranchIds);
        LinkedHashSet<Long> branchIds = new LinkedHashSet<>(requestedBranchIds);
        for (Long branchId : branchIds) {
            BranchAccessResponse branch = branchAccessService.findActiveByBusinessIdAndBranchId(actor.businessId(), branchId)
                    .orElseThrow(UserManagementAccessDeniedException::new);
            if (!branch.businessId().equals(actor.businessId())) throw new UserManagementAccessDeniedException();
            if (actor.type() == ManagementActorType.USER
                    && !userAccessService.hasActiveBranchAccess(actor.businessId(), actor.actorId(), branchId)) {
                throw new UserManagementAccessDeniedException();
            }
        }
        return branchIds;
    }

    private void assertBranchAccessible(ManagementActor actor, Long branchId) {
        if (branchId == null || branchId <= 0) throw new UserManagementAccessDeniedException();
        branchAccessService.findActiveByBusinessIdAndBranchId(actor.businessId(), branchId)
                .orElseThrow(UserManagementAccessDeniedException::new);
        if (actor.type() == ManagementActorType.USER
                && !userAccessService.hasActiveBranchAccess(actor.businessId(), actor.actorId(), branchId)) {
            throw new UserManagementAccessDeniedException();
        }
    }

    private void syncBranchAssignments(Long userId, Set<Long> requestedBranchIds, Instant now) {
        List<UserBranch> existing = userBranchRepository.findByUserId(userId);
        Map<Long, UserBranch> byBranchId = existing.stream()
                .collect(Collectors.toMap(UserBranch::getBranchId, assignment -> assignment,
                        (left, right) -> left, LinkedHashMap::new));

        for (UserBranch assignment : existing) {
            if (requestedBranchIds.contains(assignment.getBranchId())) assignment.activate(now);
            else assignment.deactivate(now);
        }
        for (Long branchId : requestedBranchIds) {
            if (!byBranchId.containsKey(branchId)) {
                userBranchRepository.save(UserBranch.assign(userId, branchId, now));
            }
        }
        userBranchRepository.flush();
    }

    private Map<Long, UserGroup> loadGroups(Long businessId, List<User> users) {
        Set<Long> groupIds = users.stream().map(User::getUserGroupId).collect(Collectors.toSet());
        return userGroupRepository.findAllById(groupIds).stream()
                .filter(group -> group.getBusinessId().equals(businessId) && !group.isArchived())
                .collect(Collectors.toMap(UserGroup::getId, group -> group));
    }

    private Map<Long, Set<Long>> loadBranchIds(List<User> users) {
        Map<Long, Set<Long>> result = new LinkedHashMap<>();
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        for (Long userId : userIds) result.put(userId, new LinkedHashSet<>());
        for (UserBranch assignment : userBranchRepository.findByUserIdInAndActiveTrue(userIds)) {
            result.computeIfAbsent(assignment.getUserId(), ignored -> new LinkedHashSet<>())
                    .add(assignment.getBranchId());
        }
        return result;
    }

    private Set<Long> activeBranchIds(Long userId) {
        return userBranchRepository.findByUserIdAndActiveTrue(userId).stream()
                .map(UserBranch::getBranchId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private User activeUser(Long businessId, Long userId) {
        return userRepository.findByIdAndBusinessId(userId, businessId)
                .filter(user -> !user.isArchived())
                .orElseThrow(UserNotFoundException::new);
    }

    private UserGroup activeGroup(Long businessId, Long groupId) {
        if (groupId == null || groupId <= 0) throw new UserGroupNotFoundException();
        return userGroupRepository.findByIdAndBusinessId(groupId, businessId)
                .filter(group -> !group.isArchived())
                .orElseThrow(UserGroupNotFoundException::new);
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
