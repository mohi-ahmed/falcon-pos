package com.spark.falcon.identity.controller;

import com.spark.falcon.identity.dto.PasswordResetRequestForm;
import com.spark.falcon.identity.exception.PasswordResetException;
import com.spark.falcon.identity.usecase.RequestPasswordResetUseCase;
import com.spark.falcon.identity.usecase.RequestPasswordResetUseCase.ResendResult;
import com.spark.falcon.identity.usecase.ResetPasswordUseCase;
import com.spark.falcon.identity.usecase.VerifyPasswordResetCodeUseCase;
import com.spark.falcon.identity.web.session.PasswordResetSession;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final VerifyPasswordResetCodeUseCase verifyPasswordResetCodeUseCase;

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    @PostMapping("/forgot-password")
    public String requestPasswordReset(
            @Valid @ModelAttribute("passwordResetRequestForm") PasswordResetRequestForm form,
            BindingResult bindingResult, HttpSession session) {

        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String normalizedEmail = form.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        requestPasswordResetUseCase.request(normalizedEmail);

        session.setAttribute(
                PasswordResetSession.EMAIL,
                normalizedEmail
        );
        session.removeAttribute(
                PasswordResetSession.VERIFIED
        );

        return "redirect:/password-reset-verification";
    }

    @PostMapping("/password-reset-verification")
    public String verifyPasswordResetCode(@RequestParam("code") String code, HttpSession session,
            RedirectAttributes redirectAttributes) {

        String email = (String) session.getAttribute(PasswordResetSession.EMAIL);
        if (email == null) {
            return "redirect:/forgot-password";
        }
        try {
            verifyPasswordResetCodeUseCase.verify(email, code);
            session.setAttribute(
                    PasswordResetSession.VERIFIED,
                    Boolean.TRUE
            );
            return "redirect:/reset-password";
        } catch (PasswordResetException exception) {

            redirectAttributes.addFlashAttribute(
                    "passwordResetError",
                    exception.getMessage()
            );

            redirectAttributes.addFlashAttribute(
                    "passwordResetReason",
                    exception.getReason()
            );

            if (exception.getReason()
                    == PasswordResetException.Reason.ATTEMPT_LIMIT_REACHED) {

                redirectAttributes.addFlashAttribute(
                        "passwordResetLocked",
                        true
                );
            }

            return "redirect:/password-reset-verification";
        }
    }

    @PostMapping("/password-reset-verification/resend")
    public String resendPasswordResetCode(
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        String email = (String) session.getAttribute(PasswordResetSession.EMAIL);

        if (email == null) {
            return "redirect:/forgot-password";
        }

        Boolean verified =
                (Boolean) session.getAttribute(
                        PasswordResetSession.VERIFIED
                );

        if (Boolean.TRUE.equals(verified)) {
            return "redirect:/reset-password";
        }

        try {
            ResendResult result =
                    requestPasswordResetUseCase.resend(email);

            if (result == ResendResult.COOLDOWN) {

                redirectAttributes.addFlashAttribute(
                        "passwordResetError",
                        "Please wait before requesting another verification code."
                );

                return "redirect:/password-reset-verification";
            }

            redirectAttributes.addFlashAttribute(
                    "passwordResetSuccess",
                    "A new verification code has been sent."
            );

        } catch (PasswordResetException exception) {

            redirectAttributes.addFlashAttribute(
                    "passwordResetError",
                    exception.getMessage()
            );
        }

        return "redirect:/password-reset-verification";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ){

        String email = (String) session.getAttribute(PasswordResetSession.EMAIL);

        if (email == null) {
            return "redirect:/forgot-password";
        }

        Boolean verified =
                (Boolean) session.getAttribute(
                        PasswordResetSession.VERIFIED
                );

        if (!Boolean.TRUE.equals(verified)) {
            return "redirect:/password-reset-verification";
        }

        if (!newPassword.equals(confirmPassword)) {

            redirectAttributes.addFlashAttribute(
                    "passwordResetError",
                    "Passwords do not match."
            );

            return "redirect:/reset-password";
        }

        try {
            resetPasswordUseCase.reset(email, newPassword);
            session.removeAttribute(PasswordResetSession.EMAIL);
            session.removeAttribute(PasswordResetSession.VERIFIED);

            return "redirect:/password-reset-complete";

        } catch (IllegalArgumentException exception) {

            redirectAttributes.addFlashAttribute(
                    "passwordResetError",
                    exception.getMessage()
            );

            return "redirect:/reset-password";

        } catch (PasswordResetException exception) {
            session.removeAttribute(PasswordResetSession.VERIFIED);

            return "redirect:/forgot-password?sessionExpired";
        }
    }


}
