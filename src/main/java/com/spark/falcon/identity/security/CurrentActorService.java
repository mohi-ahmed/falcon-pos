package com.spark.falcon.identity.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentActorService {

    public Long actorId(Long ownerFallbackId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerPrincipal principal
                && principal.isStaff() && principal.staffUserId() != null) {
            return principal.staffUserId();
        }
        return ownerFallbackId;
    }

    public boolean isStaff() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof OwnerPrincipal principal
                && principal.isStaff();
    }
}
