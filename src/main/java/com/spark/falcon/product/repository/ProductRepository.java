package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByBusinessIdAndReferenceCodeIgnoreCase(Long businessId, String referenceCode);
    boolean existsByBusinessIdAndReferenceCodeIgnoreCaseAndIdNot(Long businessId, String referenceCode, Long id);
    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);
    Optional<Product> findByIdAndBusinessIdAndArchivedAtIsNull(Long id, Long businessId);
    List<Product> findByBusinessIdOrderByDisplayOrderAscNameAsc(Long businessId);
    long countByCategoryId(Long categoryId);
    @Query("select p.categoryId, count(p) from Product p where p.categoryId in :categoryIds group by p.categoryId")
    List<Object[]> countByCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    @Query("""
            select p from Product p
            where p.businessId = :businessId
              and (:archived is null or (:archived = true and p.archivedAt is not null) or (:archived = false and p.archivedAt is null))
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:status is null or p.status = :status)
              and (:branchId is null or exists (select bp.id from BranchProduct bp where bp.productId = p.id and bp.branchId = :branchId and bp.active = true))
              and (:keyword = '' or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(p.referenceCode) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.brand, '')) like lower(concat('%', :keyword, '%'))
                   or exists (select v.id from ProductVariant v where v.productId = p.id and
                       (lower(v.variantName) like lower(concat('%', :keyword, '%')) or lower(v.sku) like lower(concat('%', :keyword, '%')))))
            """)
    Page<Product> search(@Param("businessId") Long businessId, @Param("branchId") Long branchId,
                         @Param("categoryId") Long categoryId, @Param("status") ProductStatus status,
                         @Param("archived") Boolean archived, @Param("keyword") String keyword, Pageable pageable);
}
