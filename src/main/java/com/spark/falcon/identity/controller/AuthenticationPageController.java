package com.spark.falcon.identity.controller;

import com.spark.falcon.identity.dto.PasswordResetRequestForm;
import com.spark.falcon.identity.usecase.GetPasswordResetStateUseCase;
import com.spark.falcon.identity.web.session.PasswordResetSession;
import com.spark.falcon.identity.web.support.EmailAddressMasker;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@AllArgsConstructor
@Controller
public class AuthenticationPageController {

    private final GetPasswordResetStateUseCase getPasswordResetStateUseCase;
    private final EmailAddressMasker emailAddressMasker;

    @GetMapping({"/login", "/sign-in"})
    String signIn(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String logout,
            @RequestParam(name = "expired", required = false) String expired,
            Model model
    ) {
        model.addAttribute("authenticationError", error != null);
        model.addAttribute("logoutSuccess", logout != null);
        model.addAttribute("sessionExpired", expired != null);
        return "auth/sign-in";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword(@RequestParam(name = "sessionExpired", required = false)
                                     String sessionExpired, Model model) {

        if (!model.containsAttribute("passwordResetRequestForm")) {
            model.addAttribute(
                    "passwordResetRequestForm",
                    new PasswordResetRequestForm("")
            );
        }

        model.addAttribute(
                "passwordResetSessionExpired",
                sessionExpired != null
        );

        return "auth/forgot-password";
    }

    @GetMapping("/password-reset-verification")
    String passwordResetVerification(
            HttpSession session,
            Model model
    ) {

        String email =
                (String) session.getAttribute(
                        PasswordResetSession.EMAIL
                );

        if (email == null) {
            return "redirect:/forgot-password";
        }

        GetPasswordResetStateUseCase.PasswordResetState state =
                getPasswordResetStateUseCase.getState(email);

        model.addAttribute(
                "remainingAttempts",
                state.remainingAttempts()
        );

        model.addAttribute(
                "passwordResetLocked",
                state.locked()
        );

        model.addAttribute(
                "resendRemainingSeconds",
                state.resendRemainingSeconds()
        );

        model.addAttribute(
                "maskedPasswordResetEmail",
                emailAddressMasker.mask(email)
        );


        return "auth/password-reset-verification";
    }
    @GetMapping("/reset-password")
    String resetPassword(HttpSession session) {

        String email =
                (String) session.getAttribute(
                        PasswordResetSession.EMAIL
                );

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

        return "auth/reset-password";
    }

    @GetMapping("/password-reset-complete")
    String passwordResetComplete() {
        return "auth/password-reset-complete";
    }
}
