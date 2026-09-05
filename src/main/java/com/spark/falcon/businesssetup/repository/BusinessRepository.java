package com.spark.falcon.businesssetup.repository;
import com.spark.falcon.businesssetup.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByOwnerId(Long ownerId);
    Optional<Business> findByCodeIgnoreCase(String code);
    Optional<Business> findByOwnerIdAndSetupIdempotencyKey(Long ownerId, String key);
    boolean existsByCodeIgnoreCase(String code);
}
