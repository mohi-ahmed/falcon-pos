package com.spark.falcon.product.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductSkuSequenceRepository {

    private static final String RESERVE_NEXT_NUMBER_SQL = """
            INSERT INTO product_sku_sequences (business_id, last_issued_number)
            VALUES (?, (
                SELECT GREATEST(
                    COUNT(*),
                    COALESCE(MAX(
                        CASE
                            WHEN v.sku ~ '^SKU-[0-9]{1,18}$'
                                THEN CAST(SUBSTRING(v.sku FROM 5) AS BIGINT)
                            ELSE 0
                        END
                    ), 0)
                ) + 1
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                WHERE p.business_id = ?
            ))
            ON CONFLICT (business_id)
            DO UPDATE SET last_issued_number = GREATEST(
                product_sku_sequences.last_issued_number + 1,
                EXCLUDED.last_issued_number
            )
            RETURNING last_issued_number
            """;

    private final JdbcTemplate jdbcTemplate;

    public long reserveNextNumber(Long businessId) {
        Long value = jdbcTemplate.queryForObject(
                RESERVE_NEXT_NUMBER_SQL,
                Long.class,
                businessId,
                businessId);
        if (value == null || value < 1) {
            throw new IllegalStateException("Unable to reserve a product SKU number");
        }
        return value;
    }
}
