package com.spark.falcon.identity.repository;

import com.spark.falcon.identity.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<Owner> findByEmailIgnoreCase(String email);
}

