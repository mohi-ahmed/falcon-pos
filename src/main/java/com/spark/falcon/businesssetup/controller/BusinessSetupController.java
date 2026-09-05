package com.spark.falcon.businesssetup.controller;

import com.spark.falcon.businesssetup.dto.request.BusinessSetupRequest;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.businesssetup.exception.BusinessAlreadyConfiguredException;
import com.spark.falcon.businesssetup.exception.BusinessCodeAlreadyUsedException;
import com.spark.falcon.businesssetup.mapper.BusinessSetupMapper;
import com.spark.falcon.businesssetup.service.BusinessSetupService;
import com.spark.falcon.businesssetup.validation.BusinessSetupValidator;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.security.OwnerPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/owner/setup")
@RequiredArgsConstructor
public class BusinessSetupController {
    private final BusinessSetupService service;
    private final BusinessSetupMapper mapper;
    private final BusinessSetupValidator validator;
    private final OwnerRepository ownerRepository;

    @GetMapping("/business")
    String form(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        if (service.findByOwner(principal.ownerId()).isPresent())
            return "redirect:/owner/setup/complete";

        if (!model.containsAttribute("setupRequest")) {
            BusinessSetupRequest request = new BusinessSetupRequest();
            request.setIdempotencyKey(UUID.randomUUID().toString());
            model.addAttribute("setupRequest", request);
        }
        addOwner(model, principal.ownerId());
        return "onboarding/business-setup";
    }

    @PostMapping("/business")
    String create(@Valid @ModelAttribute("setupRequest") BusinessSetupRequest request,
                  BindingResult result, @AuthenticationPrincipal OwnerPrincipal principal,
                  Model model, RedirectAttributes redirectAttributes) {
        validator.validate(request, result);
        if (result.hasErrors()) { addOwner(model, principal.ownerId()); return "onboarding/business-setup"; }
        try {
            service.create(mapper.toCommand(principal.ownerId(), request));
            redirectAttributes.addFlashAttribute("workplaceCreated", true);
            return "redirect:/owner/setup/complete";
        } catch (BusinessCodeAlreadyUsedException ex) {
            result.rejectValue("businessCode", "businessCode.used", ex.getMessage());
        } catch (BusinessAlreadyConfiguredException ex) {
            result.reject("setup.exists", ex.getMessage());
        }
        addOwner(model, principal.ownerId());
        return "onboarding/business-setup";
    }

    @GetMapping("/complete")
    String complete(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = service.findByOwner(principal.ownerId())
                .orElse(null);
        if (setup == null) return "redirect:/owner/setup/business";
        model.addAttribute("setup", setup);
        return "onboarding/setup-complete";
    }

    private void addOwner(Model model, Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId).orElseThrow();
        model.addAttribute("ownerName", owner.getFullName());
        model.addAttribute("ownerEmail", owner.getEmail());
    }
}
