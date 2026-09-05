package com.spark.falcon.identity.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OwnerSignupForm {

    @NotBlank(message = "Enter your full name.")
    @Size(min = 2, max = 120, message = "Full name must contain 2 to 120 characters.")
    private String fullName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email address is too long.")
    private String email;

    @NotBlank(message = "Create a password.")
    @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters.")
    private String password;

    private String phoneCountry;
    private String mobile;

    @Pattern(
            regexp = "^$|^\\+[1-9]\\d{6,14}$",
            message = "Enter a valid international phone number."
    )
    private String mobileInternational;

    @AssertTrue(message = "Accept the terms to continue.")
    private boolean terms;

    public RegisterOwnerCommand toCommand() {
        return new RegisterOwnerCommand(fullName, email, password, mobileInternational);
    }
}
