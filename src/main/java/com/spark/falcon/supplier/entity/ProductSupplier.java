package com.spark.falcon.supplier.entity;

import com.spark.falcon.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "product_suppliers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_supplier", columnNames = {"supplier_id", "product_id"})
}, indexes = {
        @Index(name = "idx_product_supplier_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProductSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_supplier_supplier"))
    private Supplier supplierReference;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_supplier_product"))
    private Product productReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
