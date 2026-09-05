package com.spark.falcon.identity.repository;

import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository
        extends JpaRepository<PasswordHistoryEntry, Long> {

    List<PasswordHistoryEntry>
    findTop5ByOwnerOrderByCreatedAtDesc(Owner owner);

    List<PasswordHistoryEntry>
    findByOwnerOrderByCreatedAtDesc(Owner owner);
}