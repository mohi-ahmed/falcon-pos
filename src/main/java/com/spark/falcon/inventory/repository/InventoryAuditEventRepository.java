package com.spark.falcon.inventory.repository;import com.spark.falcon.inventory.entity.InventoryAuditEvent;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface InventoryAuditEventRepository extends JpaRepository<InventoryAuditEvent,Long>{List<InventoryAuditEvent>findAllByOperationTypeAndOperationIdOrderByOccurredAtAsc(String type,Long id);}
