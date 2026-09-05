package com.spark.falcon.product.repository;
import com.spark.falcon.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);
    void deleteByProductId(Long productId);
    List<ProductImage> findByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(List<Long> productIds);
}
