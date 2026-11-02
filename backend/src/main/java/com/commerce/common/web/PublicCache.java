package com.commerce.common.web;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Tiny TTL cache for safe public reads (categories, filters, storefront config). Admin writes call
 * {@link #clear()} so changes appear immediately; the TTL is the safety net (SRS 7.2 / 12).
 */
@Component
public class PublicCache {
    private record Entry(Object value, long expiresAt) {}

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final Clock clock;

    public PublicCache(Clock clock) {
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, int ttlSeconds, Supplier<T> loader) {
        long now = clock.millis();
        Entry e = map.get(key);
        if (e != null && e.expiresAt() > now) return (T) e.value();
        T v = loader.get();
        map.put(key, new Entry(v, now + ttlSeconds * 1000L));
        return v;
    }

    public void clear() {
        map.clear();
    }
}
