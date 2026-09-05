package com.spark.falcon.identity.security;

import com.spark.falcon.businesssetup.repository.BusinessRepository;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.repository.UserRepository;
import com.spark.falcon.user.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class FalconAuthenticationProvider implements AuthenticationProvider {

    private final OwnerRepository ownerRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final UserAccessService userAccessService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName() == null
                ? ""
                : authentication.getName().trim().toLowerCase(Locale.ROOT);
        String rawPassword = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        String businessCode = authentication.getDetails() instanceof FalconWebAuthenticationDetails details
                ? details.businessCode()
                : null;

        OwnerPrincipal principal = businessCode == null || businessCode.isBlank()
                ? authenticateOwner(email, rawPassword)
                : authenticateStaff(businessCode, email, rawPassword);

        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private OwnerPrincipal authenticateOwner(String email, String rawPassword) {
        var owner = ownerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::badCredentials);
        if (!passwordEncoder.matches(rawPassword, owner.getPasswordHash())) throw badCredentials();
        OwnerPrincipal principal = OwnerPrincipal.fromOwner(owner,
                businessRepository.findByOwnerId(owner.getId()).map(value -> value.getId()).orElse(null));
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) throw new DisabledException("Account is not active");
        return principal;
    }

    private OwnerPrincipal authenticateStaff(String businessCode, String email, String rawPassword) {
        var business = businessRepository.findByCodeIgnoreCase(businessCode)
                .orElseThrow(this::badCredentials);
        User user = userRepository.findByBusinessIdAndEmailIgnoreCase(business.getId(), email)
                .filter(value -> !value.isArchived())
                .orElseThrow(this::badCredentials);
        if (!passwordEncoder.matches(rawPassword, user.authenticationPasswordHash())) throw badCredentials();
        var access = userAccessService.findActiveByBusinessIdAndUserId(business.getId(), user.getId())
                .orElseThrow(() -> new DisabledException("Account is not active"));
        if (access.branchIds().isEmpty()) throw new DisabledException("No active branch assignment");
        return OwnerPrincipal.fromStaff(user, business.getOwner().getId(), user.authenticationPasswordHash(), access);
    }

    private BadCredentialsException badCredentials() {
        return new BadCredentialsException("Invalid sign-in credentials");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
