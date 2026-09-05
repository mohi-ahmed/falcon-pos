package com.spark.falcon.branch.controller;

import com.spark.falcon.branch.dto.command.ChangeBranchStatusCommand;
import com.spark.falcon.branch.dto.command.CreateBranchCommand;
import com.spark.falcon.branch.dto.command.UpdateBranchProfileCommand;
import com.spark.falcon.branch.dto.request.CreateBranchRequest;
import com.spark.falcon.branch.dto.request.UpdateBranchRequest;
import com.spark.falcon.branch.dto.response.BranchResponse;
import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import com.spark.falcon.branch.exception.BranchCodeAlreadyUsedException;
import com.spark.falcon.branch.mapper.BranchMapper;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchService;
import com.spark.falcon.branch.validation.BranchValidator;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.user.service.UserAccessService;
import com.spark.falcon.shared.export.ExportColumn;
import com.spark.falcon.shared.export.ExportDocument;
import com.spark.falcon.shared.export.ExportResponse;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.time.ZoneId;

@Controller
@RequestMapping("/owner/branches")
@RequiredArgsConstructor
public class BranchController {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 25, 50, 100);
    private static final Set<String> SORT_FIELDS = Set.of("name", "country", "address", "createdAt");

    private final BranchService branchService;
    private final BranchContextService branchContextService;
    private final BranchMapper branchMapper;
    private final BranchValidator branchValidator;
    private final ExportResponse exportResponse;
    private final UserAccessService userAccessService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size,
                        @RequestParam(defaultValue = "createdAt") String sort,
                        @RequestParam(defaultValue = "desc") String direction,
                        @RequestParam(defaultValue = "") String search,
                        @AuthenticationPrincipal OwnerPrincipal principal,
                        Model model) {
        int safeSize = PAGE_SIZES.contains(size) ? size : 25;
        String safeSort = SORT_FIELDS.contains(sort) ? sort : "createdAt";
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(safeDirection, safeSort));
        Page<BranchResponse> branches = principal.isOwner()
                ? branchService.findAll(principal.ownerId(), search, pageable)
                : branchService.findAllAssigned(principal.businessId(), assignedBranchIds(principal), search, pageable);

        BusinessSetupResponse setup = branchContextService.resolveOwnerManagementSetup(principal.ownerId());
        model.addAttribute("setup", setup);
        model.addAttribute("branches", branches);
        model.addAttribute("search", search);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("pageSizes", PAGE_SIZES.stream().sorted().toList());
        model.addAttribute("sort", safeSort);
        model.addAttribute("direction", safeDirection.name().toLowerCase());
        model.addAttribute("currentBranchId", setup.branchId());
        model.addAttribute("canCreateBranch", principal.isOwner());
        return "branch/branch-list";
    }

    @GetMapping("/export")
    public void export(@RequestParam String format,
                       @RequestParam(defaultValue = "createdAt") String sort,
                       @RequestParam(defaultValue = "desc") String direction,
                       @RequestParam(defaultValue = "") String search,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       HttpServletResponse response) throws java.io.IOException {
        String safeSort = SORT_FIELDS.contains(sort) ? sort : "createdAt";
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        BusinessSetupResponse setup = branchContextService.resolveOwnerManagementSetup(principal.ownerId());
        BranchResponse activeBranch = branchService.findOwnedById(principal.ownerId(), setup.branchId());
        ExportDocument document = new ExportDocument("Branch List", setup.businessName(), setup.branchName(),
                zone(activeBranch.timeZone()), search.isBlank() ? Map.of() : Map.of("Search", search),
                java.util.List.of(ExportColumn.number("Serial"), ExportColumn.text("Branch Name"),
                        ExportColumn.text("Code"), ExportColumn.text("Country"), ExportColumn.text("Address"),
                        ExportColumn.dateTime("Created Date"), ExportColumn.text("Status")),
                consumer -> {
                    int page = 0;
                    Page<BranchResponse> values;
                    long serial = 1;
                    do {
                        PageRequest pageable = PageRequest.of(page++, 500, Sort.by(safeDirection, safeSort));
                        values = principal.isOwner()
                                ? branchService.findAll(principal.ownerId(), search, pageable)
                                : branchService.findAllAssigned(principal.businessId(), assignedBranchIds(principal), search, pageable);
                        for (BranchResponse branch : values.getContent()) {
                            consumer.accept(serial++, branch.name(), branch.code(), branch.country(), branch.address(),
                                    branch.createdAt(), branch.status());
                        }
                    } while (values.hasNext());
                });
        exportResponse.write(format, document, response);
    }

    private ZoneId zone(String value) {
        try { return value == null || value.isBlank() ? ZoneId.systemDefault() : ZoneId.of(value); }
        catch (java.time.DateTimeException ignored) { return ZoneId.systemDefault(); }
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        if (!model.containsAttribute("branchRequest")) {
            CreateBranchRequest request = new CreateBranchRequest();
            request.setIdempotencyKey(UUID.randomUUID().toString());
            model.addAttribute("branchRequest", request);
        }
        model.addAttribute("setup", branchContextService.resolveOwnerManagementSetup(principal.ownerId()));
        return "branch/create-branch";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("branchRequest") CreateBranchRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        branchValidator.validate(request, bindingResult);
        if (bindingResult.hasErrors()) {
            preserveValidationResult("branchRequest", request, bindingResult, redirectAttributes);
            return "redirect:/owner/branches/new";
        }
        try {
            CreateBranchCommand command = branchMapper.toCommand(principal.ownerId(), request);
            BranchResponse response = branchService.create(command);
            redirectAttributes.addFlashAttribute("successMessage", response.name()
                    + " branch created successfully. Use this branch before configuring its operational settings.");
            return "redirect:/owner/branches/" + response.id();
        } catch (BranchCodeAlreadyUsedException exception) {
            bindingResult.rejectValue("code", "branch.code.used", exception.getMessage());
            preserveValidationResult("branchRequest", request, bindingResult, redirectAttributes);
            return "redirect:/owner/branches/new";
        }
    }

    @GetMapping("/{branchId}")
    public String details(@PathVariable Long branchId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BranchResponse branch = branchService.findOwnedById(principal.ownerId(), branchId);
        BusinessSetupResponse setup = branchContextService.resolveOwnerManagementSetup(principal.ownerId());
        model.addAttribute("setup", setup);
        model.addAttribute("branch", branch);
        model.addAttribute("auditEvents", branchService.findAuditHistory(principal.ownerId(), branchId));
        model.addAttribute("isCurrentBranch", branchId.equals(setup.branchId()));
        return "branch/branch-details";
    }

    @GetMapping("/{branchId}/edit")
    public String editForm(@PathVariable Long branchId,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        BranchResponse branch = branchService.findOwnedById(principal.ownerId(), branchId);
        if (!model.containsAttribute("branchRequest")) {
            model.addAttribute("branchRequest", branchMapper.toUpdateRequest(branch));
        }
        model.addAttribute("setup", branchContextService.resolveOwnerManagementSetup(principal.ownerId()));
        model.addAttribute("branch", branch);
        return "branch/edit-branch";
    }

    @PostMapping("/{branchId}/edit")
    public String update(@PathVariable Long branchId,
                         @Valid @ModelAttribute("branchRequest") UpdateBranchRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        branchValidator.validate(request, bindingResult);
        if (bindingResult.hasErrors()) {
            preserveValidationResult("branchRequest", request, bindingResult, redirectAttributes);
            return "redirect:/owner/branches/" + branchId + "/edit";
        }
        try {
            UpdateBranchProfileCommand command = branchMapper.toUpdateCommand(principal.ownerId(), branchId, request);
            BranchResponse response = branchService.updateSettingsProfile(command);
            redirectAttributes.addFlashAttribute("successMessage", response.name() + " branch updated successfully");
            return "redirect:/owner/branches/" + branchId;
        } catch (BranchCodeAlreadyUsedException exception) {
            bindingResult.rejectValue("code", "branch.code.used", exception.getMessage());
            preserveValidationResult("branchRequest", request, bindingResult, redirectAttributes);
            return "redirect:/owner/branches/" + branchId + "/edit";
        }
    }

    @PostMapping("/{branchId}/deactivate")
    public String deactivate(@PathVariable Long branchId,
                             @AuthenticationPrincipal OwnerPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        BranchResponse response = branchService.changeStatus(
                new ChangeBranchStatusCommand(principal.ownerId(), branchId, BranchStatus.INACTIVE));
        branchContextService.clearIfSelected(response.businessId(), response.id());
        redirectAttributes.addFlashAttribute("successMessage", response.name() + " branch deactivated");
        return "redirect:/owner/branches/" + branchId;
    }

    @PostMapping("/{branchId}/activate")
    public String activate(@PathVariable Long branchId,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        BranchResponse response = branchService.changeStatus(
                new ChangeBranchStatusCommand(principal.ownerId(), branchId, BranchStatus.ACTIVE));
        redirectAttributes.addFlashAttribute("successMessage", response.name() + " branch activated");
        return "redirect:/owner/branches/" + branchId;
    }

    @GetMapping("/change")
    public String changeForm(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerManagementSetup(principal.ownerId());
        model.addAttribute("setup", setup);
        model.addAttribute("branches", principal.isOwner()
                ? branchContextService.findOwnerSelectableBranches(principal.ownerId())
                : branchContextService.findAssignedSelectableBranches(principal.businessId(), principal.staffUserId()));
        model.addAttribute("currentBranchId", setup.branchId());
        return "branch/branch-change";
    }

    @PostMapping("/change")
    public String change(@RequestParam Long branchId,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (principal.isOwner()) {
            branchContextService.selectOwnerBranch(principal.ownerId(), branchId);
        } else {
            branchContextService.selectAssignedBranch(principal.businessId(), principal.staffUserId(), branchId);
        }
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        redirectAttributes.addFlashAttribute("successMessage", "Active branch changed to " + setup.branchName());
        return principal.isOwner() ? "redirect:/owner/dashboard" : "redirect:" + staffLanding(principal);
    }

    private Set<Long> assignedBranchIds(OwnerPrincipal principal) {
        return userAccessService.findActiveByBusinessIdAndUserId(principal.businessId(), principal.staffUserId())
                .map(access -> access.branchIds())
                .orElse(Set.of());
    }

    private String staffLanding(OwnerPrincipal principal) {
        if (principal.hasPermission("POS_OPEN")) return "/owner/pos";
        if (principal.hasPermission("SELL_ACCESS")) return "/owner/sales";
        if (principal.hasPermission("PURCHASE_ACCESS")) return "/owner/purchases";
        if (principal.hasPermission("PRODUCT_ACCESS")) return "/owner/products";
        if (principal.hasPermission("SUPPLIER_ACCESS")) return "/owner/suppliers";
        if (principal.hasPermission("CUSTOMER_ACCESS")) return "/owner/customers";
        if (principal.hasPermission("PAYMENT_VIEW") || principal.hasPermission("PAYMENT_VIEW_CONSOLIDATED")) return "/owner/payments";
        if (principal.hasPermission("PAYMENT_RECEIVE_CUSTOMER_DUE")) return "/owner/payments/customer";
        if (principal.hasPermission("PAYMENT_PAY_SUPPLIER_DUE")) return "/owner/payments/supplier";
        if (principal.hasPermission("EXPENDITURE_ACCESS") || principal.hasPermission("EXPENSE_VIEW")) return "/owner/expenses";
        if (principal.hasPermission("INVENTORY_VIEW")) return "/owner/inventory";
        if (principal.hasPermission("ANALYTICS_REPORTS_ACCESS")) return "/owner/analytics";
        if (principal.hasPermission("CASH_MANAGEMENT_ACCESS")) return "/owner/cash-management";
        if (principal.hasPermission("USER_MANAGEMENT")) return "/owner/users";
        if (principal.hasPermission("SETTINGS_ACCESS")) return "/owner/settings/general";
        return "/owner/branches/change";
    }

    private void preserveValidationResult(String attributeName, Object request, BindingResult bindingResult,
                                          RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(attributeName, request);
        redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + attributeName, bindingResult);
    }
}
