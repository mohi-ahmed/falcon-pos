package com.spark.falcon.settings.controller;

import com.spark.falcon.branch.exception.BranchCodeAlreadyUsedException;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.dto.request.BranchSettingsRequest;
import com.spark.falcon.settings.dto.response.BranchSettingsResponse;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.service.BranchSettingsService;
import com.spark.falcon.settings.service.PrinterService;
import com.spark.falcon.settings.service.SettingsBrandingStorageService;
import com.spark.falcon.cashmanagement.dto.CashLocationRequest;
import com.spark.falcon.cashmanagement.dto.RegisterRequest;
import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashLocationType;
import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class BranchSettingsController {
    private final BranchContextService branchContextService;
    private final BranchSettingsService branchSettingsService;
    private final SettingsMapper settingsMapper;
    private final PrinterService printerService;
    private final SettingsBrandingStorageService brandingStorageService;
    private final CashManagementService cashManagementService;

    @GetMapping("/owner/settings/general")
    public String page(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        BranchSettingsResponse current = branchSettingsService.findByOwnerAndBranch(principal.ownerId(), setup.branchId())
                .orElseThrow(SettingsAccessDeniedException::new);

        if (!model.containsAttribute("branchSettingsRequest")) {
            model.addAttribute("branchSettingsRequest", settingsMapper.toRequest(current));
        }
        addPageModel(model, setup, current, principal.ownerId());
        return "settings/branch-settings";
    }

    @PostMapping("/owner/settings/general")
    public String update(@Valid @ModelAttribute("branchSettingsRequest") BranchSettingsRequest request,
                         BindingResult bindingResult,
                         @RequestParam(required = false) MultipartFile businessLogoFile,
                         @RequestParam(required = false) MultipartFile faviconFile,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        BranchSettingsResponse current = branchSettingsService.findByOwnerAndBranch(principal.ownerId(), setup.branchId())
                .orElseThrow(SettingsAccessDeniedException::new);
        if (bindingResult.hasErrors()) {
            addPageModel(model, setup, current, principal.ownerId());
            return "settings/branch-settings";
        }

        String storedLogo = null;
        String storedFavicon = null;
        try {
            storedLogo = brandingStorageService.storeLogo(businessLogoFile);
            storedFavicon = brandingStorageService.storeFavicon(faviconFile);
            if (storedLogo != null) request.setBusinessLogoReference(storedLogo);
            if (storedFavicon != null) request.setFaviconReference(storedFavicon);

            branchSettingsService.update(settingsMapper.toCommand(principal.ownerId(), setup.branchId(), request));
            if (storedLogo != null && current.businessLogoReference() != null
                    && !current.businessLogoReference().equals(storedLogo)) {
                brandingStorageService.deleteOwnedReference(current.businessLogoReference());
            }
            if (storedFavicon != null && current.faviconReference() != null
                    && !current.faviconReference().equals(storedFavicon)) {
                brandingStorageService.deleteOwnedReference(current.faviconReference());
            }
            redirectAttributes.addFlashAttribute("successMessage", "Branch settings updated successfully.");
            return "redirect:/owner/settings/general";
        } catch (BranchCodeAlreadyUsedException exception) {
            brandingStorageService.deleteOwnedReference(storedLogo);
            brandingStorageService.deleteOwnedReference(storedFavicon);
            bindingResult.rejectValue("code", "settings.branch.code.used", exception.getMessage());
            addPageModel(model, setup, current, principal.ownerId());
            return "settings/branch-settings";
        } catch (IllegalArgumentException exception) {
            brandingStorageService.deleteOwnedReference(storedLogo);
            brandingStorageService.deleteOwnedReference(storedFavicon);
            bindingResult.reject("settings.branch.invalid", exception.getMessage());
            model.addAttribute("errorMessage", exception.getMessage());
            addPageModel(model, setup, current, principal.ownerId());
            return "settings/branch-settings";
        }
    }

    @PostMapping("/owner/settings/registers")
    public String createRegister(@Valid @ModelAttribute("registerRequest") RegisterRequest request, BindingResult errors,
            @AuthenticationPrincipal OwnerPrincipal principal, Model model, RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (errors.hasErrors()) {
            addCashConfigurationModel(model, setup);
            return "settings/cash-configuration";
        }
        try {
            cashManagementService.createRegister(setup.businessId(), setup.branchId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Register created successfully.");
            return "redirect:/owner/settings/general#cash";
        } catch (CashManagementValidationException ex) {
            errors.rejectValue("code", "cash.register.invalid", ex.getMessage());
            model.addAttribute("errorMessage", ex.getMessage());
            addCashConfigurationModel(model, setup);
            return "settings/cash-configuration";
        }
    }

    @GetMapping("/owner/settings/cash-configuration")
    public String cashConfiguration(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        addCashConfigurationModel(model, setup);
        return "settings/cash-configuration";
    }

    @PostMapping("/owner/settings/registers/{id}/status")
    public String registerStatus(@PathVariable Long id, @RequestParam RegisterStatus status,
            @AuthenticationPrincipal OwnerPrincipal principal, RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        cashManagementService.changeRegisterStatus(setup.businessId(), setup.branchId(), id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Register status updated.");
        return "redirect:/owner/settings/general#cash";
    }

    @PostMapping("/owner/settings/cash-locations")
    public String createCashLocation(@Valid @ModelAttribute("cashLocationRequest") CashLocationRequest request,
            BindingResult errors, @AuthenticationPrincipal OwnerPrincipal principal, Model model,
            RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (errors.hasErrors()) {
            addCashConfigurationModel(model, setup);
            return "settings/cash-configuration";
        }
        try {
            cashManagementService.createCashLocation(setup.businessId(), setup.branchId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Cash location created successfully.");
            return "redirect:/owner/settings/general#cash";
        } catch (CashManagementValidationException ex) {
            errors.rejectValue("name", "cash.location.invalid", ex.getMessage());
            model.addAttribute("errorMessage", ex.getMessage());
            addCashConfigurationModel(model, setup);
            return "settings/cash-configuration";
        }
    }

    @PostMapping("/owner/settings/cash-locations/{id}/status")
    public String cashLocationStatus(@PathVariable Long id, @RequestParam CashLocationStatus status,
            @AuthenticationPrincipal OwnerPrincipal principal, RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        cashManagementService.changeCashLocationStatus(setup.businessId(), setup.branchId(), id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Cash location status updated.");
        return "redirect:/owner/settings/general#cash";
    }

    private void addCashConfigurationModel(Model model, BusinessSetupResponse setup) {
        model.addAttribute("setup", setup);
        if (!model.containsAttribute("registerRequest")) model.addAttribute("registerRequest", new RegisterRequest());
        if (!model.containsAttribute("cashLocationRequest")) model.addAttribute("cashLocationRequest", new CashLocationRequest());
        model.addAttribute("registers", cashManagementService.findRegisters(setup.businessId(), setup.branchId()));
        model.addAttribute("cashLocations", cashManagementService.findCashLocations(setup.businessId(), setup.branchId()));
        model.addAttribute("cashLocationTypes", CashLocationType.values());
        model.addAttribute("settingsTab", "general");
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private void addPageModel(Model model, BusinessSetupResponse setup, BranchSettingsResponse current, Long ownerId) {
        model.addAttribute("setup", setup);
        model.addAttribute("currentSettings", current);
        model.addAttribute("receiptPrinters", printerService.findActiveForBranch(ownerId, setup.branchId()));
        model.addAttribute("registers", cashManagementService.findRegisters(setup.businessId(), setup.branchId()));
        model.addAttribute("cashLocations", cashManagementService.findCashLocations(setup.businessId(), setup.branchId()));
        model.addAttribute("cashLocationTypes", CashLocationType.values());
        model.addAttribute("settingsTab", "general");
    }
}
