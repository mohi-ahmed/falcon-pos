package com.spark.falcon.identity.security;

import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordHistoryEntry;
import com.spark.falcon.identity.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private static final int HISTORY_LIMIT = 5;

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public void validateNotReused(
            Owner owner,
            String candidatePassword
    ) {

        if (passwordEncoder.matches(
                candidatePassword,
                owner.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "New password must be different from a recently used password"
            );
        }

        boolean reused = passwordHistoryRepository
                .findTop5ByOwnerOrderByCreatedAtDesc(owner)
                .stream()
                .anyMatch(entry ->
                        passwordEncoder.matches(
                                candidatePassword,
                                entry.getPasswordHash()
                        )
                );

        if (reused) {
            throw new IllegalArgumentException(
                    "New password must be different from a recently used password"
            );
        }
    }

    public void recordCurrentPassword(
            Owner owner,
            Instant now
    ) {

        PasswordHistoryEntry entry =
                PasswordHistoryEntry.create(
                        owner,
                        owner.getPasswordHash(),
                        now
                );

        passwordHistoryRepository.save(entry);

        pruneHistory(owner);
    }

    private void pruneHistory(Owner owner) {

        List<PasswordHistoryEntry> entries =
                passwordHistoryRepository
                        .findByOwnerOrderByCreatedAtDesc(owner);

        if (entries.size() <= HISTORY_LIMIT) {
            return;
        }

        passwordHistoryRepository.deleteAll(
                entries.subList(
                        HISTORY_LIMIT,
                        entries.size()
                )
        );
    }
}