package com.spark.falcon.identity.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditEventRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SecurityAuditEventType eventType,
            String actorIdentifier,
            String ipAddress,
            String userAgent,
            String details
    ) {
        SecurityAuditEvent event =
                SecurityAuditEvent.create(
                        eventType,
                        actorIdentifier,
                        ipAddress,
                        userAgent,
                        details,
                        Instant.now(clock)
                );

        repository.save(event);
    }

    public void recordAfterCommit(
            SecurityAuditEventType eventType,
            String actorIdentifier,
            String ipAddress,
            String userAgent,
            String details
    ) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {

            record(
                    eventType,
                    actorIdentifier,
                    ipAddress,
                    userAgent,
                    details
            );
            return;
        }

        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                record(
                                        eventType,
                                        actorIdentifier,
                                        ipAddress,
                                        userAgent,
                                        details
                                );
                            }
                        }
                );
    }
}