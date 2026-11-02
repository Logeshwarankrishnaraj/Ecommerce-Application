package com.commerce.analytics;

import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.catalog.Product;
import com.commerce.common.exception.ValidationFailedException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-only aggregates over raw events. Timezone is UTC and returned explicitly (SRS 4.3.1). */
@Service
public class AnalyticsService {
    public record Totals(long views, long wishlistAdds, long cartAdds) {}
    public record ProductRow(Long productId, String sku, String title, long views, long wishlistAdds, long cartAdds) {}
    public record DayRow(String date, long views, long wishlistAdds, long cartAdds) {}
    public record Response(String from, String to, String timezone, Totals totals, List<ProductRow> products, List<DayRow> daily) {}

    private final InteractionRepository repo;
    private final ProductRepository products;
    private final Clock clock;

    public AnalyticsService(InteractionRepository repo, ProductRepository products, Clock clock) {
        this.repo = repo;
        this.products = products;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Response report(LocalDate from, LocalDate to, int limit) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new ValidationFailedException("from", "Start date is after end date.");
        if (start.plusDays(366).isBefore(end)) throw new ValidationFailedException("from", "Range is limited to 366 days.");
        var f = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        var t = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long v = 0, w = 0, c = 0;
        for (Object[] r : repo.totals(f, t)) {
            long n = ((Number) r[1]).longValue();
            switch ((String) r[0]) {
                case ProductInteractionEvent.VIEW -> v = n;
                case ProductInteractionEvent.WISHLIST_ADD -> w = n;
                case ProductInteractionEvent.CART_ADD -> c = n;
                default -> { }
            }
        }
        Map<Long, long[]> perProduct = new HashMap<>();
        for (Object[] r : repo.perProduct(f, t)) {
            long[] a = perProduct.computeIfAbsent(((Number) r[0]).longValue(), k -> new long[3]);
            a[idx((String) r[1])] += ((Number) r[2]).longValue();
        }
        Map<Long, Product> byId = products.findAllById(perProduct.keySet()).stream().collect(Collectors.toMap(Product::getId, p -> p));
        List<ProductRow> rows = perProduct.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1] + b.getValue()[2], a.getValue()[0] + a.getValue()[1] + a.getValue()[2]))
                .limit(Math.min(Math.max(limit, 1), 100))
                .map(e -> {
                    Product p = byId.get(e.getKey());
                    return new ProductRow(e.getKey(), p == null ? "?" : p.getSku(), p == null ? "(removed)" : p.getTitle(), e.getValue()[0], e.getValue()[1], e.getValue()[2]);
                }).toList();

        TreeMap<String, long[]> days = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) days.put(d.toString(), new long[3]);
        for (Object[] r : repo.daily(f, t)) {
            long[] a = days.computeIfAbsent(String.valueOf(r[0]), k -> new long[3]);
            a[idx((String) r[1])] += ((Number) r[2]).longValue();
        }
        List<DayRow> daily = days.entrySet().stream().map(e -> new DayRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        return new Response(start.toString(), end.toString(), "UTC", new Totals(v, w, c), rows, daily);
    }

    private static int idx(String type) {
        return switch (type) {
            case ProductInteractionEvent.VIEW -> 0;
            case ProductInteractionEvent.WISHLIST_ADD -> 1;
            default -> 2;
        };
    }
}
