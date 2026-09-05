package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.ProductUnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductUnitConversionRepository extends JpaRepository<ProductUnitConversion, Long> {
    boolean existsByProductVariantIdAndSourceUnitIdAndActiveTrue(Long productVariantId, Long sourceUnitId);
    Optional<ProductUnitConversion> findByIdAndProductVariantId(Long id, Long productVariantId);
    List<ProductUnitConversion> findByProductVariantIdOrderByEffectiveFromDesc(Long productVariantId);
    List<ProductUnitConversion> findByProductVariantIdIn(List<Long> productVariantIds);
    List<ProductUnitConversion> findByProductVariantIdAndSourceUnitIdAndActiveTrueOrderByEffectiveFromDesc(
            Long productVariantId, Long sourceUnitId);
}
