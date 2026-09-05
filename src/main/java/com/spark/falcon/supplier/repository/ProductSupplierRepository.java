package com.spark.falcon.supplier.repository;

import com.spark.falcon.supplier.entity.ProductSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {

    boolean existsBySupplierIdAndProductId(Long supplierId, Long productId);

    long countBySupplierId(Long supplierId);

    List<ProductSupplier> findBySupplierIdOrderByIdAsc(Long supplierId);

    void deleteBySupplierIdAndProductId(Long supplierId, Long productId);
    List<ProductSupplier> findByProductIdInOrderByProductIdAscSupplierIdAsc(List<Long> productIds);
}
