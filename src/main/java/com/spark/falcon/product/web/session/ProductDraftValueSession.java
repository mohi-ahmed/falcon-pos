package com.spark.falcon.product.web.session;

import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class ProductDraftValueSession {

    private static final String ATTRIBUTE = ProductDraftValueSession.class.getName() + ".values";
    private static final int MAX_VALUES_PER_SESSION = 50;

    private ProductDraftValueSession() {
    }

    static String getOrCreate(HttpSession session, String namespace, String draftKey, Supplier<String> valueSupplier) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(valueSupplier, "valueSupplier");
        String key = namespacedKey(namespace, draftKey);

        synchronized (session) {
            Map<String, String> values = values(session);
            String existing = values.get(key);
            if (existing != null) {
                return existing;
            }

            String generated = Objects.requireNonNull(valueSupplier.get(), "generated draft value");
            values.put(key, generated);
            trimOldest(values);
            return generated;
        }
    }

    static void remove(HttpSession session, String namespace, String draftKey) {
        if (session == null || draftKey == null || draftKey.isBlank()) {
            return;
        }
        String key = namespacedKey(namespace, draftKey);

        synchronized (session) {
            Object value = session.getAttribute(ATTRIBUTE);
            if (!(value instanceof Map<?, ?> values)) {
                return;
            }
            values.remove(key);
            if (values.isEmpty()) {
                session.removeAttribute(ATTRIBUTE);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> values(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (value instanceof Map<?, ?>) {
            return (Map<String, String>) value;
        }

        Map<String, String> values = new LinkedHashMap<>();
        session.setAttribute(ATTRIBUTE, values);
        return values;
    }

    private static String namespacedKey(String namespace, String draftKey) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace is required");
        }
        if (draftKey == null || draftKey.isBlank()) {
            throw new IllegalArgumentException("draftKey is required");
        }
        return namespace + ':' + draftKey;
    }

    private static void trimOldest(Map<String, String> values) {
        while (values.size() > MAX_VALUES_PER_SESSION) {
            String oldestKey = values.keySet().iterator().next();
            values.remove(oldestKey);
        }
    }
}
