package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.StockImportBatch;
import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.entity.enumtype.StockImportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.time.Instant;

public interface StockImportBatchRepository extends JpaRepository<StockImportBatch, Long> {

    Optional<StockImportBatch> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    Optional<StockImportBatch> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);

    boolean existsByBusinessIdAndBranchIdAndImportPurposeAndFileFingerprintAndStatusIn(
            Long businessId, Long branchId, StockImportPurpose purpose, String fingerprint,
            Collection<StockImportStatus> statuses);

    boolean existsByBusinessIdAndBranchIdAndImportPurposeAndFileFingerprintAndIdNotAndStatusIn(
            Long businessId, Long branchId, StockImportPurpose purpose, String fingerprint, Long excludedBatchId,
            Collection<StockImportStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from StockImportBatch batch
            where batch.id = :batchId
              and batch.businessId = :businessId
              and batch.branchId = :branchId
            """)
    Optional<StockImportBatch> findForUpdate(@Param("businessId") Long businessId,
                                             @Param("branchId") Long branchId,
                                             @Param("batchId") Long batchId);

    Page<StockImportBatch> findByBusinessIdAndBranchIdOrderByUploadedAtDesc(
            Long businessId, Long branchId, Pageable pageable);

    @Query("""
            select distinct batch from StockImportBatch batch
            where batch.businessId = :businessId and batch.branchId = :branchId
              and (:#{#purpose == null} = true or batch.importPurpose = :purpose)
              and (:#{#status == null} = true or batch.status = :status)
              and (:#{#uploadedBy == null} = true or batch.uploadedByActorId = :uploadedBy)
              and (:#{#confirmedBy == null} = true or batch.confirmedByActorId = :confirmedBy)
              and (:#{#uploadedFrom == null} = true or batch.uploadedAt >= :uploadedFrom)
              and (:#{#uploadedTo == null} = true or batch.uploadedAt < :uploadedTo)
              and (:#{#confirmedFrom == null} = true or batch.confirmedAt >= :confirmedFrom)
              and (:#{#confirmedTo == null} = true or batch.confirmedAt < :confirmedTo)
              and (:#{#reversed == null} = true or (:reversed = true and batch.reversedAt is not null) or (:reversed = false and batch.reversedAt is null))
              and (:#{#batchId == null} = true or batch.id = :batchId)
              and (:query = '' or lower(batch.fileName) like lower(concat('%', :query, '%'))
                   or exists (select row.id from StockImportRow row where row.stockImportBatchId = batch.id
                              and (lower(coalesce(row.variantSku, '')) like lower(concat('%', :query, '%'))
                                   or lower(coalesce(row.batchNumber, '')) like lower(concat('%', :query, '%')))))
            """)
    Page<StockImportBatch> search(@Param("businessId") Long businessId, @Param("branchId") Long branchId,
                                  @Param("purpose") StockImportPurpose purpose, @Param("status") StockImportStatus status,
                                  @Param("uploadedBy") Long uploadedBy, @Param("confirmedBy") Long confirmedBy,
                                  @Param("uploadedFrom") Instant uploadedFrom, @Param("uploadedTo") Instant uploadedTo,
                                  @Param("confirmedFrom") Instant confirmedFrom, @Param("confirmedTo") Instant confirmedTo,
                                  @Param("reversed") Boolean reversed, @Param("batchId") Long batchId,
                                  @Param("query") String query, Pageable pageable);
}
