package com.spark.falcon.user.dto.request;

import com.spark.falcon.user.entity.enumtype.UserStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Enter the employee name.")
    @Size(min = 2, max = 120, message = "Name must contain 2 to 120 characters.")
    private String fullName;

    @NotBlank(message = "Enter the email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email address is too long.")
    private String email;

    @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "Enter a valid international phone number.")
    private String mobileNumber;

    @NotBlank(message = "Create a password.")
    @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters.")
    private String password;

    @NotBlank(message = "Confirm the password.")
    private String passwordConfirmation;

    @PastOrPresent(message = "Date of birth cannot be in the future.")
    private LocalDate dateOfBirth;

    @Size(max = 500, message = "Profile photo reference is too long.")
    private String profilePhotoReference;

    @NotNull(message = "Select a user group.")
    @Positive(message = "Select a valid user group.")
    private Long userGroupId;

    @NotEmpty(message = "Assign at least one branch.")
    private Set<@Positive Long> branchIds = new LinkedHashSet<>();

    @NotNull(message = "Select an account status.")
    private UserStatus status = UserStatus.ACTIVE;
}
