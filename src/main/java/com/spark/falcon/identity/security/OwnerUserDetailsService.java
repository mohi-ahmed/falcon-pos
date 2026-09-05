package com.spark.falcon.identity.security;

import com.spark.falcon.identity.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OwnerUserDetailsService implements UserDetailsService {

    private final OwnerRepository ownerRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return ownerRepository.findByEmailIgnoreCase(email)
                .map(OwnerPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}

