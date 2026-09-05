package com.spark.falcon.identity.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticatedSessionRevocationService {

    private final SessionRegistry sessionRegistry;

    public void revokeAllForOwner(Long ownerId) {

        sessionRegistry.getAllPrincipals().stream()
                .filter(OwnerPrincipal.class::isInstance)
                .map(OwnerPrincipal.class::cast)
                .filter(principal -> principal.ownerId().equals(ownerId))
                .forEach(principal ->
                        sessionRegistry
                                .getAllSessions(principal, false)
                                .forEach(SessionInformation::expireNow)
                );
    }
}