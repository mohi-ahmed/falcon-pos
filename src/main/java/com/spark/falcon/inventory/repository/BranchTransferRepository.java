package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.BranchTransfer;
import com.spark.falcon.inventory.entity.BranchTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchTransferRepository extends JpaRepository<BranchTransfer, Long> {
    Optional<BranchTransfer> findByIdAndBusinessId(Long id, Long businessId);
    Optional<BranchTransfer> findByBusinessIdAndIdempotencyKey(Long businessId, String key);
    List<BranchTransfer> findAllByBusinessIdAndSourceBranchIdOrBusinessIdAndDestinationBranchIdOrderByCreatedAtDesc(Long businessId, Long source, Long businessId2, Long destination);

}
