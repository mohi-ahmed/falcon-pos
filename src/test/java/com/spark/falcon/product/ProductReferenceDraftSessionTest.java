package com.spark.falcon.product;

import com.spark.falcon.product.web.session.ProductReferenceDraftSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProductReferenceDraftSessionTest {

    @Test
    void reusesReferenceCodeForTheSameDraftAcrossRefreshes() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String first = ProductReferenceDraftSession.getOrCreate(
                session, "draft-a", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));
        String refreshed = ProductReferenceDraftSession.getOrCreate(
                session, "draft-a", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));

        assertThat(first).isEqualTo("PRD-000001");
        assertThat(refreshed).isEqualTo(first);
        assertThat(reservations).hasValue(1);
    }

    @Test
    void keepsDifferentProductDraftsIndependent() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String firstDraft = ProductReferenceDraftSession.getOrCreate(
                session, "draft-a", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));
        String secondDraft = ProductReferenceDraftSession.getOrCreate(
                session, "draft-b", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));

        assertThat(firstDraft).isEqualTo("PRD-000001");
        assertThat(secondDraft).isEqualTo("PRD-000002");
        assertThat(reservations).hasValue(2);
    }

    @Test
    void releasesDraftAfterSuccessfulSave() {
        MockHttpSession session = new MockHttpSession();
        AtomicInteger reservations = new AtomicInteger();

        String beforeSave = ProductReferenceDraftSession.getOrCreate(
                session, "draft-a", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));
        ProductReferenceDraftSession.remove(session, "draft-a");
        String afterSave = ProductReferenceDraftSession.getOrCreate(
                session, "draft-a", () -> "PRD-%06d".formatted(reservations.incrementAndGet()));

        assertThat(beforeSave).isEqualTo("PRD-000001");
        assertThat(afterSave).isEqualTo("PRD-000002");
        assertThat(reservations).hasValue(2);
    }
}
