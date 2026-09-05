package com.spark.falcon.identity.controller;

import com.spark.falcon.identity.dto.OwnerRegistrationResult;
import com.spark.falcon.identity.dto.OwnerSignupForm;
import com.spark.falcon.identity.exception.EmailAlreadyRegisteredException;
import com.spark.falcon.identity.usecase.RegisterOwnerUseCase;
import com.spark.falcon.identity.web.session.OwnerVerificationSession;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class OwnerRegistrationController {

    private static final String SIGNUP_VIEW = "auth/owner-signup";
    private static final String VERIFY_EMAIL_REDIRECT = "redirect:/owner/email-verification";

    private final RegisterOwnerUseCase registerOwnerUseCase;

    @GetMapping({"/signup", "/owner/signup"})
    public String showSignup(Model model) {
        if (!model.containsAttribute("ownerSignupForm")) {
            model.addAttribute("ownerSignupForm", new OwnerSignupForm());
        }
        return SIGNUP_VIEW;
    }

    @PostMapping({"/signup", "/owner/signup"})
    public String register(
            @Valid @ModelAttribute("ownerSignupForm") OwnerSignupForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        if (bindingResult.hasErrors()) {
            return SIGNUP_VIEW;
        }
        try {
            OwnerRegistrationResult result = registerOwnerUseCase.register(form.toCommand());
            session.setAttribute(OwnerVerificationSession.EMAIL, result.email());
            redirectAttributes.addFlashAttribute("verificationNotice", "We sent a six-digit code to your email.");
            return VERIFY_EMAIL_REDIRECT;
        } catch (EmailAlreadyRegisteredException exception) {
            bindingResult.rejectValue("email", "email.registered", exception.getMessage());
            return SIGNUP_VIEW;
        }
    }
}
