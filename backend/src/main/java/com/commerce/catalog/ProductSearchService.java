package com.commerce.catalog;

import com.commerce.catalog.CatalogDtos.*;
import com.commerce.catalog.CatalogRepositories.*;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.util.Like;
import com.commerce.common.web.PublicCache;
import com.commerce.inventory.Inventory;
import com.commerce.inventory.InventoryRepositories.InventoryRepository;
import com.commerce.lookup.AttributeDefinition;
import com.commerce.lookup.ListOfValue;
import com.commerce.lookup.LookupRepositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public catalogue: validated, parameterized, bounded search + filters + detail (SRS 7.2). */
@Service
@Transactional(readOnly = true)
public class ProductSearchService {
    public static final int MAX_PAGE_SIZE = 50;
    static final Set<String> SORTS = Set.of("newest", "price_asc", "price_desc", "title");

    public record Query(String q, String category, BigDecimal minPrice, BigDecimal maxPrice, String sort, boolean inStock,
                        List<String> attr, int page, int size) {}

    private final EntityManager em;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ProductAttributeValueRepository attributeValues;
    private final AttributeDefinitionRepository definitions;
    private final ListOfValueRepository lovs;
    private final InventoryRepository inventory;
    private final CategoryService categoryService;
    private final ProductViewAssembler assembler;
    private final PublicCache cache;

    public ProductSearchService(EntityManager em, ProductRepository products, CategoryRepository categories, ProductImageRepository images,
                                ProductAttributeValueRepository attributeValues, AttributeDefinitionRepository definitions,
                                ListOfValueRepository lovs, InventoryRepository inventory, CategoryService categoryService,
                                ProductViewAssembler assembler, PublicCache cache) {
        this.em = em;
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.attributeValues = attributeValues;
        this.definitions = definitions;
        this.lovs = lovs;
        this.inventory = inventory;
        this.categoryService = categoryService;
        this.assembler = assembler;
        this.cache = cache;
    }

    public FiltersResponse filters() {
        return cache.get("products:filters", 30, () -> {
            List<FilterAttribute> attrs = new ArrayList<>();
            for (AttributeDefinition d : definitions.findByActiveTrueOrderBySortOrderAsc()) {
                if (!d.isFilterable() || d.getLovGroupCode() == null) continue;
                List<FilterValue> vals = lovs.findByGroupCodeAndActiveTrueOrderBySortOrderAscDisplayLabelAsc(d.getLovGroupCode())
                        .stream().map(v -> new FilterValue(v.getValueCode(), v.getDisplayLabel())).toList();
                attrs.add(new FilterAttribute(d.getCode(), d.getLabel(), vals));
            }
            Object[] mm = (Object[]) em.createQuery("select min(coalesce(p.salePrice, p.basePrice)), max(coalesce(p.salePrice, p.basePrice)) from Product p, Category c "
                    + "where c.id = p.categoryId and c.active = true and p.status = 'PUBLISHED'").getSingleResult();
            BigDecimal min = mm[0] == null ? BigDecimal.ZERO : (BigDecimal) mm[0];
            BigDecimal max = mm[1] == null ? BigDecimal.ZERO : (BigDecimal) mm[1];
            return new FiltersResponse(attrs, new PriceRange(min, max));
        });
    }

    public PageResponse<ProductCard> search(Query q) {
        int page = Math.max(q.page(), 0);
        int size = Math.min(Math.max(q.size(), 1), MAX_PAGE_SIZE);
        String sort = q.sort() == null || q.sort().isBlank() ? "newest" : q.sort();
        if (!SORTS.contains(sort)) throw new ValidationFailedException("sort", "Unsupported sort option.");
        if (q.q() != null && q.q().length() > 100) throw new ValidationFailedException("q", "Search text is too long (max 100).");
        if (q.minPrice() != null && q.minPrice().signum() < 0) throw new ValidationFailedException("minPrice", "Price cannot be negative.");
        if (q.maxPrice() != null && q.minPrice() != null && q.maxPrice().compareTo(q.minPrice()) < 0) throw new ValidationFailedException("maxPrice", "Maximum price is below the minimum.");

        Map<String, Set<String>> attrFilters = parseAttrFilters(q.attr());
        StringBuilder base = new StringBuilder(" from Product p, Category c where c.id = p.categoryId and c.active = true and p.status = 'PUBLISHED'");
        Map<String, Object> params = new HashMap<>();

        String like = Like.contains(q.q());
        if (like != null) {
            base.append(" and (lower(p.title) like :q escape '!' or lower(p.sku) like :q escape '!' or lower(p.description) like :q escape '!')");
            params.put("q", like);
        }
        if (q.category() != null && !q.category().isBlank()) {
            Optional<Category> cat = categories.findBySlug(q.category().trim());
            if (cat.isEmpty() || !cat.get().isActive()) return PageResponse.of(List.of(), page, size, 0);
            base.append(" and p.categoryId in :catIds");
            params.put("catIds", categoryService.subtreeIds(cat.get().getId()));
        }
        if (q.minPrice() != null) {
            base.append(" and coalesce(p.salePrice, p.basePrice) >= :minPrice");
            params.put("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            base.append(" and coalesce(p.salePrice, p.basePrice) <= :maxPrice");
            params.put("maxPrice", q.maxPrice());
        }
        if (q.inStock()) {
            base.append(" and exists (select 1 from Inventory i where i.productId = p.id and i.onHand - i.reserved > 0)");
        }
        int n = 0;
        for (Map.Entry<String, Set<String>> e : attrFilters.entrySet()) {
            base.append(" and exists (select 1 from ProductAttributeValue v where v.id.productId = p.id and v.id.attributeCode = :ac").append(n)
                    .append(" and v.valueCode in :av").append(n).append(")");
            params.put("ac" + n, e.getKey());
            params.put("av" + n, e.getValue());
            n++;
        }
        String order = switch (sort) {
            case "price_asc" -> " order by coalesce(p.salePrice, p.basePrice) asc, p.id asc";
            case "price_desc" -> " order by coalesce(p.salePrice, p.basePrice) desc, p.id asc";
            case "title" -> " order by lower(p.title) asc, p.id asc";
            default -> " order by p.createdAt desc, p.id desc";
        };

        TypedQuery<Long> count = em.createQuery("select count(p)" + base, Long.class);
        params.forEach(count::setParameter);
        long total = count.getSingleResult();
        TypedQuery<Product> select = em.createQuery("select p" + base + order, Product.class);
        params.forEach(select::setParameter);
        select.setFirstResult(page * size);
        select.setMaxResults(size);
        return PageResponse.of(assembler.cards(select.getResultList()), page, size, total);
    }

    /** attr=code:value pairs are checked against active, filterable definitions and active lookup values. */
    private Map<String, Set<String>> parseAttrFilters(List<String> raw) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        if (raw.size() > 20) throw new ValidationFailedException("attr", "Too many filters.");
        Map<String, Set<String>> allowed = new HashMap<>();
        for (FilterAttribute a : filters().attributes()) {
            allowed.put(a.code(), a.values().stream().map(FilterValue::valueCode).collect(Collectors.toSet()));
        }
        for (String pair : raw) {
            int idx = pair == null ? -1 : pair.indexOf(':');
            if (idx <= 0 || idx == pair.length() - 1) throw new ValidationFailedException("attr", "Filters must look like code:value.");
            String code = pair.substring(0, idx);
            String value = pair.substring(idx + 1);
            Set<String> ok = allowed.get(code);
            if (ok == null) throw new ValidationFailedException("attr", "Unsupported filter '" + safe(code) + "'.");
            if (!ok.contains(value)) throw new ValidationFailedException("attr", "Unsupported value for filter '" + code + "'.");
            out.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(value);
        }
        return out;
    }

    private static String safe(String s) {
        return s.length() > 30 ? s.substring(0, 30) : s.replaceAll("[^A-Za-z0-9_.-]", "?");
    }

    public ProductDetail detail(String slug) {
        Product p = products.findBySlug(slug).filter(Product::isPublished).orElseThrow(() -> new ResourceNotFoundException("Product"));
        Category cat = categories.findById(p.getCategoryId()).filter(Category::isActive).orElseThrow(() -> new ResourceNotFoundException("Product"));
        List<ImageRef> imgs = images.findByProductIdOrderBySortOrderAsc(p.getId()).stream().map(ProductViewAssembler::imageRef).toList();
        Inventory inv = inventory.findById(p.getId()).orElse(null);
        int available = inv == null ? 0 : Math.max(inv.available(), 0);
        String status = available <= 0 ? "OUT_OF_STOCK" : (inv != null && available <= inv.getLowStockThreshold() ? "LOW_STOCK" : "IN_STOCK");
        Map<String, AttributeDefinition> defs = definitions.findAllByOrderBySortOrderAsc().stream().collect(Collectors.toMap(AttributeDefinition::getCode, d -> d));
        List<AttributeView> attrs = new ArrayList<>();
        for (ProductAttributeValue v : attributeValues.findByProduct(p.getId())) {
            AttributeDefinition d = defs.get(v.getId().getAttributeCode());
            if (d == null || !d.isActive()) continue;
            String label = v.getTextValue();
            if (d.getLovGroupCode() != null && v.getValueCode() != null) {
                label = lovs.findByGroupCodeAndValueCode(d.getLovGroupCode(), v.getValueCode()).map(ListOfValue::getDisplayLabel).orElse(v.getValueCode());
            }
            attrs.add(new AttributeView(d.getCode(), d.getLabel(), v.getValueCode(), label));
        }
        ImageRef primary = imgs.isEmpty() ? null : imgs.get(0);
        return new ProductDetail(p.getId(), p.getSlug(), p.getSku(), p.getTitle(), p.getBasePrice(), p.getSalePrice(), p.effectivePrice(), p.getCurrency(),
                primary, available > 0, cat.getName(), p.getDescription(), cat.getId(), cat.getSlug(), imgs, attrs, status, Math.min(available, 10),
                p.getSeoTitle(), p.getSeoDescription());
    }

    /** Documented rule: other published products in the same category, newest first, max 4. */
    public List<ProductCard> related(Long productId) {
        Product p = products.findById(productId).filter(Product::isPublished).orElseThrow(() -> new ResourceNotFoundException("Product"));
        return assembler.cards(products.findRelated(p.getCategoryId(), p.getId(), PageRequest.of(0, 4)));
    }
}
