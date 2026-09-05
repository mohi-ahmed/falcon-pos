package com.spark.falcon.settings.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.dto.request.PaymentMethodRequest;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.exception.SettingsAccessDeniedException;
import com.spark.falcon.settings.exception.SettingsCodeAlreadyUsedException;
import com.spark.falcon.settings.mapper.SettingsMapper;
import com.spark.falcon.settings.service.BranchSettingsService;
import com.spark.falcon.settings.service.PaymentMethodService;
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
@RequestMapping("/owner/settings/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final BranchContextService branchContextService;
    private final BranchSettingsService branchSettingsService;
    private final PaymentMethodService paymentMethodService;
    private final SettingsMapper settingsMapper;

    @GetMapping
    public String page(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (!model.containsAttribute("paymentMethodRequest")) {
            PaymentMethodRequest request = new PaymentMethodRequest();
            request.setBranchIds(new LinkedHashSet<>(java.util.Set.of(setup.branchId())));
            model.addAttribute("paymentMethodRequest", request);
        }
        addPageModel(model, setup, principal.ownerId());
        return "settings/payment-methods";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("paymentMethodRequest") PaymentMethodRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        if (bindingResult.hasErrors()) {
            addPageModel(model, setup, principal.ownerId());
            return "settings/payment-methods";
        }
        try {
            paymentMethodService.create(settingsMapper.toCreateCommand(principal.ownerId(), request));
            redirectAttributes.addFlashAttribute("successMessage", "Payment method created successfully.");
            return "redirect:/owner/settings/payment-methods";
        } catch (SettingsCodeAlreadyUsedException exception) {
            bindingResult.rejectValue("code", "settings.payment.code.used", exception.getMessage());
            addPageModel(model, setup, principal.ownerId());
            return "settings/payment-methods";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute PaymentMethodRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return "redirect:/owner/settings/payment-methods";
        }
        try {
            paymentMethodService.update(settingsMapper.toUpdateCommand(principal.ownerId(), id, request));
            redirectAttributes.addFlashAttribute("successMessage", "Payment method updated successfully.");
        } catch (SettingsCodeAlreadyUsedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/settings/payment-methods";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id,
                         @RequestParam ConfigurationStatus status,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        paymentMethodService.changeStatus(principal.ownerId(), id, status);
        redirectAttributes.addFlashAttribute("successMessage", "Payment method status updated.");
        return "redirect:/owner/settings/payment-methods";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        paymentMethodService.archive(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Payment method archived.");
        return "redirect:/owner/settings/payment-methods";
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private void addPageModel(Model model, BusinessSetupResponse setup, Long ownerId) {
        model.addAttribute("setup", setup);
        model.addAttribute("paymentMethods", paymentMethodService.findAll(ownerId));
        model.addAttribute("availableBranches", branchContextService.findOwnerSelectableBranches(ownerId));
        int defaultRecordsPerPage = branchSettingsService.findByOwnerAndBranch(ownerId, setup.branchId())
                .map(response -> response.recordsPerPage() == null ? 10 : response.recordsPerPage())
                .orElse(10);
        model.addAttribute("defaultRecordsPerPage", defaultRecordsPerPage);
        model.addAttribute("settingsTab", "payment-methods");
    }

    private String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty() ? "Please review the form and try again." : result.getAllErrors().getFirst().getDefaultMessage();
    }
}
