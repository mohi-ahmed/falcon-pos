package com.spark.falcon.product.repository;
import com.spark.falcon.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndBusinessId(Long id, Long businessId);
    List<Category> findByBusinessIdOrderByDisplayOrderAscNameAsc(Long businessId);
    @Query("""
            select c from Category c where c.businessId = :businessId
              and (:archived is null or (:archived = true and c.archivedAt is not null) or (:archived = false and c.archivedAt is null))
              and (:status is null or c.status = :status)
              and (:branchId is null or exists (select bc.id from BranchCategory bc where bc.categoryId = c.id and bc.branchId = :branchId and bc.active = true))
              and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')) or lower(c.slug) like lower(concat('%', :keyword, '%')))
            """)
    Page<Category> search(@Param("businessId") Long businessId, @Param("branchId") Long branchId,
                          @Param("status") ProductStatus status, @Param("archived") Boolean archived,
                          @Param("keyword") String keyword, Pageable pageable);
}
