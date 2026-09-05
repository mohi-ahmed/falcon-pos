package com.spark.falcon.user.validation;

import com.spark.falcon.identity.security.PasswordPolicy;
import com.spark.falcon.user.dto.command.CreateUserCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserManagementValidator {

    private final PasswordPolicy passwordPolicy;

    public void validateCreateUser(CreateUserCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        passwordPolicy.validate(command.rawPassword());
        if (!command.rawPassword().equals(command.passwordConfirmation())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        validateBranchIds(command.branchIds());
        if (command.userGroupId() == null || command.userGroupId() <= 0) {
            throw new IllegalArgumentException("userGroupId is invalid");
        }
    }

    public void validateBranchIds(Set<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            throw new IllegalArgumentException("At least one branch assignment is required");
        }
        if (branchIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("branchIds contains an invalid branch id");
        }
    }

    public Set<Long> copyIds(Set<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeSlug(String name, String requestedSlug) {
        String source = requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug;
        if (source == null || source.isBlank()) throw new IllegalArgumentException("group name is required");
        String ascii = Normalizer.normalize(source, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (ascii.isBlank()) throw new IllegalArgumentException("group slug could not be generated");
        if (ascii.length() > 140) throw new IllegalArgumentException("group slug is too long");
        return ascii;
    }

    public String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
