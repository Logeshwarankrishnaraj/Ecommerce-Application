package com.commerce.common.web;

import com.commerce.common.exception.RateLimitedException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small in-memory sliding-window limiter. Sufficient for a single instance; replace with a shared store
 * (Redis/bucket4j) when scaling horizontally (documented limitation).
 */
@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws {@link RateLimitedException} when more than {@code limit} calls happened within the window. */
    public void check(String key, int limit, int windowSeconds) {
        long now = clock.millis();
        long cutoff = now - windowSeconds * 1000L;
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
            if (q.size() >= limit) throw new RateLimitedException();
            q.addLast(now);
        }
        if (hits.size() > 50_000) {
            hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff; } });
        }
    }
}
