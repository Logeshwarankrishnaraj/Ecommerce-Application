package com.commerce.analytics;

import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.util.Hashing;
import com.commerce.common.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records product interactions with de-duplication (SRS 7.5). Rule: one VIEW per product per pseudonymous visitor
 * per 30 minutes; one WISHLIST_ADD / CART_ADD per product per customer per 10 minutes. Only successful actions are
 * recorded because callers invoke this after their own success.
 */
@Service
public class InteractionService {
    static final Duration VIEW_WINDOW = Duration.ofMinutes(30);
    static final Duration ADD_WINDOW = Duration.ofMinutes(10);

    private final InteractionRepository repo;
    private final ProductRepository products;
    private final RateLimiter limiter;
    private final Clock clock;

    public InteractionService(InteractionRepository repo, ProductRepository products, RateLimiter limiter, Clock clock) {
        this.repo = repo;
        this.products = products;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public void recordView(Long productId, HttpServletRequest req, String ip) {
        limiter.check("view-ip:" + ip, 120, 60);
        if (!products.findById(productId).filter(p -> p.isPublished()).isPresent()) throw new ResourceNotFoundException("Product");
        String ua = req.getHeader("User-Agent") == null ? "" : req.getHeader("User-Agent");
        String key = Hashing.sha256Hex(ip + "|" + ua).substring(0, 32);
        record(ProductInteractionEvent.VIEW, productId, key, null, VIEW_WINDOW);
    }

    /** Called by wishlist/cart services after a successful add. Joins the caller's transaction. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAdd(String type, Long productId, Long userId) {
        String key = Hashing.sha256Hex("u:" + userId).substring(0, 32);   // pseudonymous
        record(type, productId, key, null, ADD_WINDOW);
    }

    private void record(String type, Long productId, String sessionKey, Long userId, Duration window) {
        Instant now = clock.instant();
        String dedupe = type + ":" + productId + ":" + sessionKey;
        if (repo.existsByDedupeKeyAndCreatedAtAfter(dedupe, now.minus(window))) return;
        ProductInteractionEvent e = new ProductInteractionEvent();
        e.setProductId(productId);
        e.setEventType(type);
        e.setSessionKey(sessionKey);
        e.setUserId(userId);
        e.setDedupeKey(dedupe);
        e.setCreatedAt(now);
        repo.save(e);
    }

    /** Retention: raw events kept 400 days. */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void purgeOld() {
        repo.deleteOlderThan(clock.instant().minus(Duration.ofDays(400)));
    }
}
