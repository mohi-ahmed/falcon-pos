package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.ProductAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAuditEventRepository extends JpaRepository<ProductAuditEvent, Long> {
}
