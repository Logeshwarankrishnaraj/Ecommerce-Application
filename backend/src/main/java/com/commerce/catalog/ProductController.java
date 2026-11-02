package com.commerce.catalog;

import com.commerce.analytics.InteractionService;
import com.commerce.catalog.CatalogDtos.*;
import com.commerce.common.api.PageResponse;
import com.commerce.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Public catalogue endpoints. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductSearchService search;
    private final InteractionService interactions;

    public ProductController(ProductSearchService search, InteractionService interactions) {
        this.search = search;
        this.interactions = interactions;
    }

    @GetMapping
    public PageResponse<ProductCard> list(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
                                          @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                          @RequestParam(required = false) String sort, @RequestParam(defaultValue = "false") boolean inStock,
                                          @RequestParam(required = false) List<String> attr, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "12") int size) {
        return search.search(new ProductSearchService.Query(q, category, minPrice, maxPrice, sort, inStock, attr, page, size));
    }

    @GetMapping("/filters")
    public FiltersResponse filters() {
        return search.filters();
    }

    @GetMapping("/{slug}")
    public ProductDetail detail(@PathVariable String slug) {
        return search.detail(slug);
    }

    @GetMapping("/{productId}/related")
    public List<ProductCard> related(@PathVariable Long productId) {
        return search.related(productId);
    }

    @PostMapping("/{productId}/interactions/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable Long productId, HttpServletRequest req) {
        interactions.recordView(productId, req, ClientIp.of(req));
    }
}
