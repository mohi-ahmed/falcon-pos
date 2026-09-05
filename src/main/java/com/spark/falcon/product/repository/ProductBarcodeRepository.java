package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {
    boolean existsByBusinessIdAndBarcodeAndActiveTrue(Long businessId, String barcode);
    Optional<ProductBarcode> findByIdAndBusinessId(Long id, Long businessId);
    Optional<ProductBarcode> findByBusinessIdAndBarcodeAndActiveTrue(Long businessId, String barcode);
    List<ProductBarcode> findByBusinessIdAndActiveTrueOrderByBarcodeAsc(Long businessId);
    List<ProductBarcode> findByProductVariantIdOrderByIdAsc(Long productVariantId);
}
