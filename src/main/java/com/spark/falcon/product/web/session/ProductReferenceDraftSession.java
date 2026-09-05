package com.spark.falcon.product.web.session;

import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class ProductReferenceDraftSession {

    private static final String ATTRIBUTE = ProductReferenceDraftSession.class.getName() + ".drafts";
    private static final int MAX_DRAFTS_PER_SESSION = 20;

    private ProductReferenceDraftSession() {
    }

    public static String getOrCreate(HttpSession session, String draftId, Supplier<String> codeSupplier) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(codeSupplier, "codeSupplier");
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId is required");
        }

        synchronized (session) {
            Map<String, String> drafts = drafts(session);
            String existing = drafts.get(draftId);
            if (existing != null) {
                return existing;
            }

            String generated = Objects.requireNonNull(codeSupplier.get(), "generated reference code");
            drafts.put(draftId, generated);
            trimOldestDrafts(drafts);
            return generated;
        }
    }

    public static void remove(HttpSession session, String draftId) {
        if (session == null || draftId == null || draftId.isBlank()) {
            return;
        }

        synchronized (session) {
            Object value = session.getAttribute(ATTRIBUTE);
            if (!(value instanceof Map<?, ?> drafts)) {
                return;
            }
            drafts.remove(draftId);
            if (drafts.isEmpty()) {
                session.removeAttribute(ATTRIBUTE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> drafts(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (value instanceof Map<?, ?>) {
            return (Map<String, String>) value;
        }

        Map<String, String> drafts = new LinkedHashMap<>();
        session.setAttribute(ATTRIBUTE, drafts);
        return drafts;
    }

    private static void trimOldestDrafts(Map<String, String> drafts) {
        while (drafts.size() > MAX_DRAFTS_PER_SESSION) {
            String oldestDraftId = drafts.keySet().iterator().next();
            drafts.remove(oldestDraftId);
        }
    }
}
