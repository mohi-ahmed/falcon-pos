package com.spark.falcon.identity.security;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    public void validate(String password) {

        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters"
            );
        }

        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 characters"
            );
        }

        boolean hasUppercase = password.chars()
                .anyMatch(Character::isUpperCase);

        boolean hasLowercase = password.chars()
                .anyMatch(Character::isLowerCase);

        boolean hasDigit = password.chars()
                .anyMatch(Character::isDigit);

        if (!hasUppercase || !hasLowercase || !hasDigit) {
            throw new IllegalArgumentException(
                    "Password must contain uppercase, lowercase and number"
            );
        }
    }
}