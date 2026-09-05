package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    boolean existsByProductIdAndSkuIgnoreCase(Long productId, String sku);
    boolean existsByProductIdAndSkuIgnoreCaseAndIdNot(Long productId, String sku, Long id);
    Optional<ProductVariant> findByIdAndProductId(Long id, Long productId);
    Optional<ProductVariant> findByIdAndStatus(Long id, ProductStatus status);
    List<ProductVariant> findBySkuIgnoreCaseAndStatus(String sku, ProductStatus status);
    List<ProductVariant> findByProductIdOrderByVariantNameAsc(Long productId);
    @Query("select v from ProductVariant v join Product p on p.id = v.productId where p.businessId = :businessId and v.productId in :productIds order by v.productId, v.variantName")
    List<ProductVariant> findForBusinessAndProductIds(@Param("businessId") Long businessId, @Param("productIds") List<Long> productIds);

    @Query("select count(v) from ProductVariant v join Product p on p.id = v.productId where p.businessId = :businessId and lower(v.sku) = lower(:sku)")
    long countForBusinessBySkuIgnoreCase(@Param("businessId") Long businessId, @Param("sku") String sku);
}
