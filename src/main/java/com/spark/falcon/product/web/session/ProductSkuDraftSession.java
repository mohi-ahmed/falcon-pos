package com.spark.falcon.product.web.session;

import jakarta.servlet.http.HttpSession;

import java.util.function.Supplier;

public final class ProductSkuDraftSession {

    private static final String NAMESPACE = "sku";
    private static final String NEW_PRODUCT_PREFIX = "new-product:";
    private static final String PRODUCT_VARIANT_PREFIX = "product-variant:";

    private ProductSkuDraftSession() {
    }

    public static String getOrCreateForNewProduct(HttpSession session, String draftId, Supplier<String> skuSupplier) {
        return ProductDraftValueSession.getOrCreate(session, NAMESPACE, NEW_PRODUCT_PREFIX + required(draftId, "draftId"), skuSupplier);
    }

    public static void removeForNewProduct(HttpSession session, String draftId) {
        if (draftId != null && !draftId.isBlank()) {
            ProductDraftValueSession.remove(session, NAMESPACE, NEW_PRODUCT_PREFIX + draftId);
        }
    }

    public static String getOrCreateForProductVariant(HttpSession session,
                                                       Long productId,
                                                       String draftId,
                                                       Supplier<String> skuSupplier) {
        return ProductDraftValueSession.getOrCreate(
                session,
                NAMESPACE,
                productVariantKey(productId, draftId),
                skuSupplier);
    }

    public static void removeForProductVariant(HttpSession session, Long productId, String draftId) {
        if (productId == null || draftId == null || draftId.isBlank()) {
            return;
        }
        ProductDraftValueSession.remove(session, NAMESPACE, productVariantKey(productId, draftId));
    }

    private static String productVariantKey(Long productId, String draftId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        return PRODUCT_VARIANT_PREFIX + productId + ':' + required(draftId, "draftId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
