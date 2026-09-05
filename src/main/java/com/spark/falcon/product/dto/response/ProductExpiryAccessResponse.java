package com.spark.falcon.product.dto.response;

public record ProductExpiryAccessResponse(
        Long productId,
        Long variantId,
        Integer expiryAlertBeforeDays
) {
}
