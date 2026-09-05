package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.BranchTransferReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchTransferReceiptRepository extends JpaRepository<BranchTransferReceipt, Long> {
    Optional<BranchTransferReceipt> findByBusinessIdAndTransferIdAndIdempotencyKey(
            Long businessId, Long transferId, String idempotencyKey);

    List<BranchTransferReceipt> findByBusinessIdAndTransferIdOrderByReceivedAtAscIdAsc(
            Long businessId, Long transferId);
}
