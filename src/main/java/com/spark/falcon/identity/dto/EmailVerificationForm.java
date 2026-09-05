package com.spark.falcon.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationForm(
        @NotBlank String email,
        @Pattern(regexp = "\\d{6}", message = "Enter the six-digit code from your email.") String code
) { }
