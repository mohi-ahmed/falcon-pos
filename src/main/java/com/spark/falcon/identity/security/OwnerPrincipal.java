package com.spark.falcon.identity.security;

import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.OwnerStatus;
import com.spark.falcon.user.dto.response.UserAccessResponse;
import com.spark.falcon.user.entity.User;
import com.spark.falcon.user.entity.enumtype.UserStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public record OwnerPrincipal(
        Long ownerId,
        Long businessId,
        Long staffUserId,
        String userGroupName,
        Set<String> permissionCodes,
        String username,
        String password,
        OwnerStatus status,
        UserStatus staffStatus
) implements UserDetails {

    public OwnerPrincipal {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }

    public static OwnerPrincipal from(Owner owner) {
        return fromOwner(owner, null);
    }

    public static OwnerPrincipal fromOwner(Owner owner, Long businessId) {
        return new OwnerPrincipal(owner.getId(), businessId, null, null, Set.of(), owner.getEmail(),
                owner.getPasswordHash(), owner.getStatus(), null);
    }

    public static OwnerPrincipal fromStaff(User user, Long ownerId, String passwordHash, UserAccessResponse access) {
        return new OwnerPrincipal(ownerId, access.businessId(), user.getId(), access.userGroupName(),
                access.permissionCodes(), user.getEmail(), passwordHash, null, access.status());
    }

    public OwnerPrincipal refreshStaff(UserAccessResponse access) {
        if (!isStaff()) return this;
        return new OwnerPrincipal(ownerId, access.businessId(), access.userId(), access.userGroupName(),
                access.permissionCodes(), access.email(), password, null, access.status());
    }

    public boolean isOwner() {
        return staffUserId == null;
    }

    public boolean isStaff() {
        return staffUserId != null;
    }

    public Long actorId() {
        return isOwner() ? ownerId : staffUserId;
    }

    public boolean hasPermission(String permissionCode) {
        return isOwner() || (permissionCode != null && permissionCodes.contains(permissionCode));
    }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        LinkedHashSet<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        if (isOwner()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
            permissionCodes.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }
        return Set.copyOf(authorities);
    }

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return isStaff() || status != OwnerStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return isOwner() ? status == OwnerStatus.ACTIVE : staffStatus == UserStatus.ACTIVE;
    }
}
