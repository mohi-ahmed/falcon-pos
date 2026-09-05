package com.spark.falcon.settings.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.dto.request.PrinterRequest;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.entity.enumtype.PrinterConnectionType;
import com.spark.falcon.settings.entity.enumtype.PrinterType;
import com.spark.falcon.settings.service.BranchSettingsService;
import com.spark.falcon.settings.service.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class PrinterSettingsController {
    private final BranchContextService branchContextService;
    private final BranchSettingsService branchSettingsService;
    private final PrinterService printerService;

    @GetMapping("/owner/settings/printers")
    public String page(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(principal.ownerId());
        if (!model.containsAttribute("printerRequest")) {
            PrinterRequest request = new PrinterRequest();
            request.setCharactersPerLine(42);
            request.setPort(9100);
            request.setBranchIds(new LinkedHashSet<>(Set.of(setup.branchId())));
            model.addAttribute("printerRequest", request);
        }
        model.addAttribute("setup", setup);
        model.addAttribute("printers", printerService.findAll(principal.ownerId()));
        model.addAttribute("availableBranches", branchContextService.findOwnerSelectableBranches(principal.ownerId()));
        int defaultRecordsPerPage = branchSettingsService.findByOwnerAndBranch(principal.ownerId(), setup.branchId())
                .map(response -> response.recordsPerPage() == null ? 10 : response.recordsPerPage())
                .orElse(10);
        model.addAttribute("defaultRecordsPerPage", defaultRecordsPerPage);
        model.addAttribute("printerTypes", PrinterType.values());
        model.addAttribute("connectionTypes", PrinterConnectionType.values());
        model.addAttribute("settingsTab", "printers");
        return "settings/printers";
    }

    @PostMapping("/owner/settings/printers")
    public String create(@Valid @ModelAttribute("printerRequest") PrinterRequest request, BindingResult errors,
                         @AuthenticationPrincipal OwnerPrincipal principal, Model model,
                         RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) return page(principal, model);
        try {
            printerService.create(principal.ownerId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Printer created successfully.");
            return "redirect:/owner/settings/printers";
        } catch (IllegalArgumentException exception) {
            errors.reject("printer.configuration.invalid", exception.getMessage());
            model.addAttribute("errorMessage", exception.getMessage());
            return page(principal, model);
        }
    }

    @PostMapping("/owner/settings/printers/{id}")
    public String update(@PathVariable Long id, @Valid PrinterRequest request, BindingResult errors,
                         @AuthenticationPrincipal OwnerPrincipal principal, RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please review the printer configuration.");
        } else {
            try {
                printerService.update(principal.ownerId(), id, request);
                redirectAttributes.addFlashAttribute("successMessage", "Printer updated successfully.");
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            }
        }
        return "redirect:/owner/settings/printers";
    }

    @PostMapping("/owner/settings/printers/{id}/status")
    public String status(@PathVariable Long id, @RequestParam ConfigurationStatus status,
                         @AuthenticationPrincipal OwnerPrincipal principal, RedirectAttributes redirectAttributes) {
        printerService.changeStatus(principal.ownerId(), id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Printer status updated.");
        return "redirect:/owner/settings/printers";
    }

    @PostMapping("/owner/settings/printers/{id}/archive")
    public String archive(@PathVariable Long id, @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        printerService.archive(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Printer archived.");
        return "redirect:/owner/settings/printers";
    }

    @PostMapping("/owner/settings/printers/{id}/test")
    public String test(@PathVariable Long id, @AuthenticationPrincipal OwnerPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        try {
            printerService.recordTest(principal.ownerId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Test print sent successfully.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/settings/printers";
    }
}
