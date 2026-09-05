package com.spark.falcon.product;

import com.spark.falcon.product.web.session.ProductSkuDraftSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSkuDraftSessionTest {

    @Test
    void reusesInitialSkuForTheSameProductDraftAcrossRefreshes() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String first = ProductSkuDraftSession.getOrCreateForNewProduct(
                session, "draft-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));
        String refreshed = ProductSkuDraftSession.getOrCreateForNewProduct(
                session, "draft-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));

        assertThat(first).isEqualTo("SKU-000001");
        assertThat(refreshed).isEqualTo(first);
        assertThat(reservations).hasValue(1);
    }

    @Test
    void keepsSeparateAddVariantDraftsIndependentAndRefreshStable() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String first = ProductSkuDraftSession.getOrCreateForProductVariant(
                session, 25L, "variant-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));
        String refreshed = ProductSkuDraftSession.getOrCreateForProductVariant(
                session, 25L, "variant-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));
        String secondDraft = ProductSkuDraftSession.getOrCreateForProductVariant(
                session, 25L, "variant-b", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));

        assertThat(refreshed).isEqualTo(first);
        assertThat(secondDraft).isEqualTo("SKU-000002");
        assertThat(reservations).hasValue(2);
    }

    @Test
    void releasesAddVariantDraftAfterSuccessfulSave() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String beforeSave = ProductSkuDraftSession.getOrCreateForProductVariant(
                session, 25L, "variant-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));
        ProductSkuDraftSession.removeForProductVariant(session, 25L, "variant-a");
        String afterSave = ProductSkuDraftSession.getOrCreateForProductVariant(
                session, 25L, "variant-a", () -> "SKU-%06d".formatted(reservations.incrementAndGet()));

        assertThat(beforeSave).isEqualTo("SKU-000001");
        assertThat(afterSave).isEqualTo("SKU-000002");
        assertThat(reservations).hasValue(2);
    }
}
