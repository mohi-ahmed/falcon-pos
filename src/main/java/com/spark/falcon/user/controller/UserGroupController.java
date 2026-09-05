package com.spark.falcon.user.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.user.dto.command.ManagementActor;
import com.spark.falcon.user.dto.request.GroupPermissionRequest;
import com.spark.falcon.user.dto.request.UserGroupRequest;
import com.spark.falcon.user.dto.response.UserGroupResponse;
import com.spark.falcon.user.exception.*;
import com.spark.falcon.user.mapper.UserManagementMapper;
import com.spark.falcon.user.service.PermissionService;
import com.spark.falcon.user.service.UserGroupService;
import com.spark.falcon.user.service.UserManagementActorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;

@Controller
@RequestMapping("/owner/user-groups")
@RequiredArgsConstructor
public class UserGroupController {

    private final BranchContextService branchContextService;
    private final UserManagementActorService actorService;
    private final UserGroupService userGroupService;
    private final PermissionService permissionService;
    private final UserManagementMapper mapper;

    @GetMapping
    public String list(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (!model.containsAttribute("userGroupRequest")) model.addAttribute("userGroupRequest", new UserGroupRequest());
        addCommonModel(model, setup, actor);
        model.addAttribute("userSection", "groups");
        return "user-groups/user-group-list";
    }

    @GetMapping("/new")
    public String createPage(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (!model.containsAttribute("userGroupRequest")) model.addAttribute("userGroupRequest", new UserGroupRequest());
        addCommonModel(model, setup, actor);
        model.addAttribute("userSection", "add-group");
        return "user-groups/add-user-group";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userGroupRequest") UserGroupRequest request,
                         BindingResult bindingResult,
                         @RequestParam(defaultValue = "add") String source,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (bindingResult.hasErrors()) return renderCreateError(source, setup, actor, request, model);
        try {
            UserGroupResponse created = userGroupService.create(mapper.toCreateCommand(actor, request));
            redirectAttributes.addFlashAttribute("successMessage",
                    created.name() + " group created. Configure its permissions next.");
            return "list".equalsIgnoreCase(source)
                    ? "redirect:/owner/user-groups"
                    : "redirect:/owner/user-groups/" + created.id() + "/permissions";
        } catch (UserGroupSlugAlreadyUsedException exception) {
            bindingResult.rejectValue("slug", "group.slug.used", exception.getMessage());
            return renderCreateError(source, setup, actor, request, model);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("group.create.invalid", exception.getMessage());
            return renderCreateError(source, setup, actor, request, model);
        }
    }

    @GetMapping("/{id}/edit")
    public String editPage(@PathVariable Long id,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        UserGroupResponse group = userGroupService.findById(actor, id);
        if (!model.containsAttribute("userGroupRequest")) {
            UserGroupRequest request = new UserGroupRequest();
            request.setName(group.name());
            request.setSlug(group.slug());
            model.addAttribute("userGroupRequest", request);
        }
        addCommonModel(model, setup, actor);
        model.addAttribute("group", group);
        model.addAttribute("userSection", "groups");
        return "user-groups/edit-user-group";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userGroupRequest") UserGroupRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (bindingResult.hasErrors()) {
            addCommonModel(model, setup, actor);
            model.addAttribute("group", userGroupService.findById(actor, id));
            model.addAttribute("userSection", "groups");
            return "user-groups/edit-user-group";
        }
        try {
            userGroupService.update(mapper.toUpdateCommand(actor, id, request));
            redirectAttributes.addFlashAttribute("successMessage", "User group updated successfully.");
            return "redirect:/owner/user-groups";
        } catch (UserGroupSlugAlreadyUsedException exception) {
            bindingResult.rejectValue("slug", "group.slug.used", exception.getMessage());
        } catch (ProtectedUserGroupException exception) {
            bindingResult.reject("group.protected", exception.getMessage());
        }
        addCommonModel(model, setup, actor);
        model.addAttribute("group", userGroupService.findById(actor, id));
        model.addAttribute("userSection", "groups");
        return "user-groups/edit-user-group";
    }

    @GetMapping("/{id}/permissions")
    public String permissionsPage(@PathVariable Long id,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        UserGroupResponse group = userGroupService.findById(actor, id);
        if (!model.containsAttribute("groupPermissionRequest")) {
            GroupPermissionRequest request = new GroupPermissionRequest();
            request.setPermissionIds(new LinkedHashSet<>(group.permissionIds()));
            model.addAttribute("groupPermissionRequest", request);
        }
        addCommonModel(model, setup, actor);
        model.addAttribute("group", group);
        model.addAttribute("userSection", "groups");
        return "user-groups/permissions";
    }

    @PostMapping("/{id}/permissions")
    public String configurePermissions(@PathVariable Long id,
                                       @Valid @ModelAttribute("groupPermissionRequest") GroupPermissionRequest request,
                                       BindingResult bindingResult,
                                       @AuthenticationPrincipal OwnerPrincipal principal,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        ManagementActor actor = actorService.forOwner(principal.ownerId());
        if (bindingResult.hasErrors()) {
            addCommonModel(model, setup, actor);
            model.addAttribute("group", userGroupService.findById(actor, id));
            model.addAttribute("userSection", "groups");
            return "user-groups/permissions";
        }
        try {
            userGroupService.configurePermissions(mapper.toPermissionCommand(actor, id, request));
            redirectAttributes.addFlashAttribute("successMessage", "Group permissions updated successfully.");
            return "redirect:/owner/user-groups";
        } catch (PermissionNotFoundException | ProtectedUserGroupException exception) {
            bindingResult.reject("group.permissions.invalid", exception.getMessage());
            addCommonModel(model, setup, actor);
            model.addAttribute("group", userGroupService.findById(actor, id));
            model.addAttribute("userSection", "groups");
            return "user-groups/permissions";
        }
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        try {
            userGroupService.archive(actorService.forOwner(principal.ownerId()), id);
            redirectAttributes.addFlashAttribute("successMessage", "User group archived successfully.");
        } catch (UserGroupInUseException | ProtectedUserGroupException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/user-groups";
    }

    private String renderCreateError(String source, BusinessSetupResponse setup, ManagementActor actor,
                                     UserGroupRequest request, Model model) {
        model.addAttribute("userGroupRequest", request);
        addCommonModel(model, setup, actor);
        if ("list".equalsIgnoreCase(source)) {
            model.addAttribute("openCreatePanel", true);
            model.addAttribute("userSection", "groups");
            return "user-groups/user-group-list";
        }
        model.addAttribute("userSection", "add-group");
        return "user-groups/add-user-group";
    }

    private void addCommonModel(Model model, BusinessSetupResponse setup, ManagementActor actor) {
        model.addAttribute("setup", setup);
        model.addAttribute("groups", userGroupService.findAll(actor));
        model.addAttribute("permissions", permissionService.findAllActive());
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }
}
