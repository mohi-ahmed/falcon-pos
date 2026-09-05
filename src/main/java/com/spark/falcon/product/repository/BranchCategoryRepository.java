package com.spark.falcon.product.repository;
import com.spark.falcon.product.entity.BranchCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BranchCategoryRepository extends JpaRepository<BranchCategory, Long> {
    List<BranchCategory> findByCategoryId(Long categoryId);
    List<BranchCategory> findByCategoryIdIn(List<Long> categoryIds);
}
