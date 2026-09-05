package com.spark.falcon.identity.controller;

import com.spark.falcon.identity.dto.EmailVerificationForm;
import com.spark.falcon.identity.exception.EmailVerificationException;
import com.spark.falcon.identity.usecase.IssueEmailVerificationUseCase;
import com.spark.falcon.identity.usecase.VerifyEmailUseCase;
import com.spark.falcon.identity.web.session.OwnerVerificationSession;
import com.spark.falcon.identity.web.support.EmailAddressMasker;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner/email-verification")
@RequiredArgsConstructor
public class OwnerEmailVerificationController {
    private static final String VIEW = "auth/owner-verification";
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final IssueEmailVerificationUseCase issueEmailVerificationUseCase;
    private final EmailAddressMasker emailAddressMasker;

    @GetMapping
    String show(HttpSession session, Model model) {
        String email = (String) session.getAttribute(OwnerVerificationSession.EMAIL);
        if (email == null) return "redirect:/signup";
        if (!model.containsAttribute("verificationForm")) {
            model.addAttribute("verificationForm", new EmailVerificationForm(email, ""));
        }
        model.addAttribute("maskedEmail", emailAddressMasker.mask(email));
        return VIEW;
    }

    @PostMapping
    String verify(@Valid @ModelAttribute("verificationForm") EmailVerificationForm form,
                  BindingResult errors, HttpSession session, Model model) {
        String email = (String) session.getAttribute(OwnerVerificationSession.EMAIL);
        if (email == null) return "redirect:/signup";
        if (!email.equalsIgnoreCase(form.email())) errors.reject("verification.invalid", "The verification request is invalid.");
        if (!errors.hasErrors()) {
            try {
                verifyEmailUseCase.verify(email, form.code());
                session.removeAttribute(OwnerVerificationSession.EMAIL);
                return "redirect:/login?verified";
            } catch (EmailVerificationException exception) {
                errors.reject("verification.failed", exception.getMessage());
            }
        }
        model.addAttribute("maskedEmail", emailAddressMasker.mask(email));
        return VIEW;
    }

    @PostMapping("/resend")
    String resend(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(OwnerVerificationSession.EMAIL);
        if (email == null) return "redirect:/signup";
        try {
            issueEmailVerificationUseCase.resend(email);
            redirectAttributes.addFlashAttribute("verificationNotice", "A new verification code was sent.");
        } catch (EmailVerificationException exception) {
            redirectAttributes.addFlashAttribute("verificationError", exception.getMessage());
        }
        return "redirect:/owner/email-verification";
    }

}
