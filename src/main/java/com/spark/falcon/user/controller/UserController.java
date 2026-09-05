package com.spark.falcon.user.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.dto.request.CreateUserRequest;
import com.spark.falcon.user.dto.request.UpdateUserRequest;
import com.spark.falcon.user.dto.response.UserResponse;
import com.spark.falcon.user.entity.enumtype.UserStatus;
import com.spark.falcon.user.exception.UserEmailAlreadyUsedException;
import com.spark.falcon.user.exception.UserManagementAccessDeniedException;
import com.spark.falcon.user.mapper.UserManagementMapper;
import com.spark.falcon.user.service.PermissionService;
import com.spark.falcon.user.service.UserGroupService;
import com.spark.falcon.user.service.UserManagementActorService;
import com.spark.falcon.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;
import java.util.Set;

@Controller
@RequestMapping("/owner/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 25, 50, 100);

    private final BranchContextService branchContextService;
    private final UserManagementActorService actorService;
    private final UserService userService;
    private final UserGroupService userGroupService;
    private final PermissionService permissionService;
    private final UserManagementMapper mapper;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "createdAt,desc") String sort,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 10;
        int safePage = Math.max(0, page);
        Page<UserResponse> users = userService.findByBranch(actor, setup.branchId(), q,
                PageRequest.of(safePage, safeSize, sort(sort)));

        if (!model.containsAttribute("createUserRequest")) {
            CreateUserRequest request = new CreateUserRequest();
            request.setBranchIds(new LinkedHashSet<>(Set.of(setup.branchId())));
            model.addAttribute("createUserRequest", request);
        }
        addCommonModel(model, setup, actor);
        addBranchOptions(model, principal.ownerId());
        model.addAttribute("users", users);
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("selectedSort", normalizeSortKey(sort));
        model.addAttribute("userSection", "users");
        return "users/user-list";
    }

    @GetMapping("/new")
    public String createPage(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (!model.containsAttribute("createUserRequest")) {
            CreateUserRequest request = new CreateUserRequest();
            request.setBranchIds(new LinkedHashSet<>(Set.of(setup.branchId())));
            request.setStatus(UserStatus.ACTIVE);
            model.addAttribute("createUserRequest", request);
        }
        addCommonModel(model, setup, actor);
        addBranchOptions(model, principal.ownerId());
        model.addAttribute("recentUsers", userService.findByBranch(actor, setup.branchId(), null,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
        model.addAttribute("userSection", "add-user");
        return "users/add-user";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("createUserRequest") CreateUserRequest request,
                         BindingResult bindingResult,
                         @RequestParam(defaultValue = "add") String source,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (bindingResult.hasErrors()) {
            return renderCreateError(source, setup, actor, principal.ownerId(), request, model);
        }
        try {
            UserResponse created = userService.create(mapper.toCreateCommand(actor, request));
            redirectAttributes.addFlashAttribute("successMessage",
                    created.fullName() + " was added to the staff directory.");
            return "list".equalsIgnoreCase(source)
                    ? "redirect:/owner/users"
                    : "redirect:/owner/users/new";
        } catch (UserEmailAlreadyUsedException exception) {
            bindingResult.rejectValue("email", "user.email.used", exception.getMessage());
            return renderCreateError(source, setup, actor, principal.ownerId(), request, model);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("user.create.invalid", exception.getMessage());
            return renderCreateError(source, setup, actor, principal.ownerId(), request, model);
        }
    }

    @GetMapping("/{id}")
    public String profile(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        UserResponse user = userService.findById(actor, id);
        addCommonModel(model, setup, actor);
        model.addAttribute("user", user);
        model.addAttribute("userSection", "users");
        return "users/user-profile";
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        UserResponse user = userService.findById(actor, id);
        if (!model.containsAttribute("updateUserRequest")) {
            model.addAttribute("updateUserRequest", toUpdateRequest(user));
        }
        addCommonModel(model, setup, actor);
        addBranchOptions(model, principal.ownerId());
        model.addAttribute("user", user);
        model.addAttribute("userSection", "users");
        return "users/edit-user";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("updateUserRequest") UpdateUserRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (bindingResult.hasErrors()) {
            addCommonModel(model, setup, actor);
            addBranchOptions(model, principal.ownerId());
            model.addAttribute("user", userService.findById(actor, id));
            model.addAttribute("userSection", "users");
            return "users/edit-user";
        }
        try {
            UserResponse updated = userService.update(mapper.toUpdateCommand(actor, id, request));
            redirectAttributes.addFlashAttribute("successMessage",
                    updated.fullName() + " was updated successfully.");
            return "redirect:/owner/users/" + id;
        } catch (UserEmailAlreadyUsedException exception) {
            bindingResult.rejectValue("email", "user.email.used", exception.getMessage());
            addCommonModel(model, setup, actor);
            addBranchOptions(model, principal.ownerId());
            model.addAttribute("user", userService.findById(actor, id));
            model.addAttribute("userSection", "users");
            return "users/edit-user";
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("user.update.invalid", exception.getMessage());
            addCommonModel(model, setup, actor);
            addBranchOptions(model, principal.ownerId());
            model.addAttribute("user", userService.findById(actor, id));
            model.addAttribute("userSection", "users");
            return "users/edit-user";
        }
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        try {
            userService.archive(actorService.forOwner(principal.ownerId()), id);
            redirectAttributes.addFlashAttribute("successMessage", "Staff account archived successfully.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/users";
    }

    private String renderCreateError(String source, BusinessSetupResponse setup, ManagementActor actor, Long ownerId,
                                     CreateUserRequest request, Model model) {
        model.addAttribute("createUserRequest", request);
        addCommonModel(model, setup, actor);
        addBranchOptions(model, ownerId);
        if ("list".equalsIgnoreCase(source)) {
            model.addAttribute("users", userService.findByBranch(actor, setup.branchId(), null,
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))));
            model.addAttribute("query", "");
            model.addAttribute("selectedSize", 10);
            model.addAttribute("selectedSort", "created-desc");
            model.addAttribute("userSection", "users");
            model.addAttribute("openCreatePanel", true);
            return "users/user-list";
        }
        model.addAttribute("recentUsers", userService.findByBranch(actor, setup.branchId(), null,
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
        model.addAttribute("userSection", "add-user");
        return "users/add-user";
    }

    private void addCommonModel(Model model, BusinessSetupResponse setup, ManagementActor actor) {
        model.addAttribute("setup", setup);
        model.addAttribute("userGroups", userGroupService.findAll(actor));
        model.addAttribute("permissions", permissionService.findAllActive());
        model.addAttribute("userStatuses", UserStatus.values());
    }

    private void addBranchOptions(Model model, Long ownerId) {
        model.addAttribute("availableBranches", branchContextService.findOwnerSelectableBranches(ownerId));
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private UpdateUserRequest toUpdateRequest(UserResponse user) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName(user.fullName());
        request.setEmail(user.email());
        request.setMobileNumber(user.mobileNumber());
        request.setDateOfBirth(user.dateOfBirth());
        request.setProfilePhotoReference(user.profilePhotoReference());
        request.setUserGroupId(user.userGroupId());
        request.setBranchIds(new LinkedHashSet<>(user.branchIds()));
        request.setStatus(user.status());
        return request;
    }

    private Sort sort(String requested) {
        return switch (normalizeSortKey(requested)) {
            case "name-asc" -> Sort.by(Sort.Direction.ASC, "fullName");
            case "name-desc" -> Sort.by(Sort.Direction.DESC, "fullName");
            case "email-asc" -> Sort.by(Sort.Direction.ASC, "email");
            case "status-asc" -> Sort.by(Sort.Direction.ASC, "status").and(Sort.by("fullName"));
            case "created-asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private String normalizeSortKey(String requested) {
        if (requested == null) return "created-desc";
        return switch (requested.trim().toLowerCase()) {
            case "name-asc", "fullname,asc" -> "name-asc";
            case "name-desc", "fullname,desc" -> "name-desc";
            case "email-asc", "email,asc" -> "email-asc";
            case "status-asc", "status,asc" -> "status-asc";
            case "created-asc", "createdat,asc" -> "created-asc";
            default -> "created-desc";
        };
    }
}
