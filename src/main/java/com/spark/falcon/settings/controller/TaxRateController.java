package com.spark.falcon.settings.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.dto.request.TaxRateRequest;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsCodeAlreadyUsedException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.service.BranchSettingsService;
import com.spark.falcon.settings.service.TaxRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner/settings/taxes")
@RequiredArgsConstructor
public class TaxRateController {
    private final BranchContextService branchContextService;
    private final BranchSettingsService branchSettingsService;
    private final TaxRateService taxRateService;
    private final SettingsMapper settingsMapper;

    @GetMapping
    public String page(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (!model.containsAttribute("taxRateRequest")) model.addAttribute("taxRateRequest", new TaxRateRequest());
        addPageModel(model, setup, principal.ownerId());
        return "settings/taxes";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("taxRateRequest") TaxRateRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (bindingResult.hasErrors()) {
            addPageModel(model, setup, principal.ownerId());
            return "settings/taxes";
        }
        try {
            taxRateService.create(settingsMapper.toCreateCommand(principal.ownerId(), request));
            redirectAttributes.addFlashAttribute("successMessage", "Tax rate created successfully.");
            return "redirect:/owner/settings/taxes";
        } catch (SettingsCodeAlreadyUsedException exception) {
            bindingResult.rejectValue("code", "settings.tax.code.used", exception.getMessage());
            addPageModel(model, setup, principal.ownerId());
            return "settings/taxes";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute TaxRateRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return "redirect:/owner/settings/taxes";
        }
        try {
            taxRateService.update(settingsMapper.toUpdateCommand(principal.ownerId(), id, request));
            redirectAttributes.addFlashAttribute("successMessage", "Tax rate updated successfully.");
        } catch (SettingsCodeAlreadyUsedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/settings/taxes";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id,
                         @RequestParam ConfigurationStatus status,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        taxRateService.changeStatus(principal.ownerId(), id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Tax rate status updated.");
        return "redirect:/owner/settings/taxes";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        taxRateService.archive(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Tax rate archived.");
        return "redirect:/owner/settings/taxes";
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private void addPageModel(Model model, BusinessSetupResponse setup, Long ownerId) {
        model.addAttribute("setup", setup);
        model.addAttribute("taxRates", taxRateService.findAll(ownerId));
        int defaultRecordsPerPage = branchSettingsService.findByOwnerAndBranch(ownerId, setup.branchId())
                .map(response -> response.recordsPerPage() == null ? 10 : response.recordsPerPage())
                .orElse(10);
        model.addAttribute("defaultRecordsPerPage", defaultRecordsPerPage);
        model.addAttribute("settingsTab", "taxes");
    }

    private String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty() ? "Please review the form and try again." : result.getAllErrors().getFirst().getDefaultMessage();
    }
}
