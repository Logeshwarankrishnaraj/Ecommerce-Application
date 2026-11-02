package com.commerce.catalog;

import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogDtos.*;
import com.commerce.catalog.CatalogRepositories.*;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.*;
import com.commerce.common.util.Like;
import com.commerce.common.util.Money;
import com.commerce.common.util.Slugs;
import com.commerce.inventory.Inventory;
import com.commerce.inventory.InventoryRepositories.InventoryRepository;
import com.commerce.inventory.InventoryService;
import com.commerce.lookup.AttributeDefinition;
import com.commerce.lookup.LookupRepositories.*;
import com.commerce.media.ImageStorageService;
import com.commerce.media.ProductImageData;
import com.commerce.media.ProductImageDataRepository;
import com.commerce.security.CurrentUser;
import com.commerce.shop.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductService {
    static final int MAX_IMAGES = 5;
    static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{1,63}$");
    static final Set<String> RESERVED_SLUGS = Set.of("filters", "new");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ProductAttributeValueRepository attributeValues;
    private final AttributeDefinitionRepository definitions;
    private final ListOfValueRepository lovs;
    private final InventoryRepository inventoryRepo;
    private final InventoryService inventory;
    private final ImageStorageService storage;
    private final ProductImageDataRepository imageData;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;

    public AdminProductService(ProductRepository products, CategoryRepository categories, ProductImageRepository images,
                               ProductAttributeValueRepository attributeValues, AttributeDefinitionRepository definitions,
                               ListOfValueRepository lovs, InventoryRepository inventoryRepo, InventoryService inventory,
                               ImageStorageService storage, ProductImageDataRepository imageData, SettingsService settings, AuditService audit, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.imageData = imageData;
        this.attributeValues = attributeValues;
        this.definitions = definitions;
        this.lovs = lovs;
        this.inventoryRepo = inventoryRepo;
        this.inventory = inventory;
        this.storage = storage;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResponse<AdminProductRow> list(String q, String status, Long categoryId, int page, int size) {
        if (status != null && !status.isBlank() && !Set.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new ValidationFailedException("status", "Unknown status.");
        }
        var result = products.adminSearch(status == null || status.isBlank() ? null : status, categoryId, Like.contains(q),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, Inventory> inv = inventoryRepo.findByProductIdIn(ids).stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        Map<Long, ProductImage> primary = new HashMap<>();
        for (ProductImage i : images.findByProductIdInOrderBySortOrderAsc(ids)) primary.putIfAbsent(i.getProductId(), i);
        Map<Long, String> cats = categories.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
        return PageResponse.of(result, p -> {
            Inventory i = inv.get(p.getId());
            ProductImage pi = primary.get(p.getId());
            return new AdminProductRow(p.getId(), p.getSku(), p.getTitle(), p.getSlug(), p.getBasePrice(), p.getSalePrice(), p.getStatus(),
                    cats.getOrDefault(p.getCategoryId(), "-"), i == null ? 0 : i.getOnHand(), i == null ? 0 : i.getReserved(),
                    i == null ? 0 : i.available(), pi == null ? null : ProductViewAssembler.imageRef(pi).thumbUrl(), p.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public AdminProduct get(Long id) {
        return view(load(id));
    }

    private Product load(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product"));
    }

    private AdminProduct view(Product p) {
        Inventory i = inventoryRepo.findById(p.getId()).orElse(null);
        List<AttributeInput> attrs = attributeValues.findByProduct(p.getId()).stream()
                .map(v -> new AttributeInput(v.getId().getAttributeCode(), v.getValueCode(), v.getTextValue())).toList();
        List<ImageRef> imgs = images.findByProductIdOrderBySortOrderAsc(p.getId()).stream().map(ProductViewAssembler::imageRef).toList();
        return new AdminProduct(p.getId(), p.getSku(), p.getTitle(), p.getSlug(), p.getDescription(), p.getCategoryId(), p.getBasePrice(),
                p.getSalePrice(), p.getCurrency(), p.getSeoTitle(), p.getSeoDescription(), i == null ? null : i.getLowStockThreshold(),
                p.isFeatured(), attrs, p.getStatus(), imgs, i == null ? 0 : i.getOnHand(), i == null ? 0 : i.getReserved(),
                i == null ? 0 : i.available(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    // ------------------------------------------------------------------ create / update

    @Transactional
    public AdminProduct create(ProductUpsert req) {
        Product p = new Product();
        p.setCreatedAt(clock.instant());
        applyCore(p, req, true);
        int initial = req.initialStock() == null ? 0 : req.initialStock();
        if (initial < 0 || initial > 1_000_000) throw new ValidationFailedException("initialStock", "Initial stock must be between 0 and 1,000,000.");
        int threshold = req.lowStockThreshold() == null ? settings.getInt("inventory.lowStockDefault", 5) : req.lowStockThreshold();
        if (threshold < 0 || threshold > 100_000) throw new ValidationFailedException("lowStockThreshold", "Threshold must be between 0 and 100,000.");
        p = products.saveAndFlush(p);
        inventory.initialise(p.getId(), initial, threshold, CurrentUser.label());
        saveAttributes(p.getId(), req.attributes());
        audit.record("PRODUCT_CREATE", "PRODUCT", p.getId(), p.getSku());
        return view(p);
    }

    @Transactional
    public AdminProduct update(Long id, ProductUpsert req) {
        Product p = load(id);
        if (req.version() != null && req.version() != p.getVersion()) {
            throw new ConflictException("This product was changed by someone else. Reload and try again.");
        }
        applyCore(p, req, false);
        if (req.lowStockThreshold() != null) {
            if (req.lowStockThreshold() < 0 || req.lowStockThreshold() > 100_000) throw new ValidationFailedException("lowStockThreshold", "Threshold must be between 0 and 100,000.");
            inventoryRepo.findById(id).ifPresent(i -> i.setLowStockThreshold(req.lowStockThreshold()));
        }
        saveAttributes(id, req.attributes());
        products.flush();
        audit.record("PRODUCT_UPDATE", "PRODUCT", id, p.getSku());
        return view(p);
    }

    /** Shared validation + field mapping (also reused by bulk import via {@link #validate}). */
    void applyCore(Product p, ProductUpsert r, boolean creating) {
        Validated v = validate(r, p.getId(), creating ? null : p.getSlug());
        p.setSku(v.sku());
        p.setTitle(v.title());
        p.setSlug(v.slug());
        p.setDescription(v.description());
        p.setCategoryId(v.categoryId());
        p.setBasePrice(v.basePrice());
        p.setSalePrice(v.salePrice());
        p.setCurrency(v.currency());
        p.setSeoTitle(v.seoTitle());
        p.setSeoDescription(v.seoDescription());
        if (r.featured() != null) p.setFeatured(r.featured());
        p.setUpdatedAt(clock.instant());
    }

    public record Validated(String sku, String title, String slug, String description, Long categoryId, BigDecimal basePrice,
                            BigDecimal salePrice, String currency, String seoTitle, String seoDescription) {}

    /** Pure-ish validation shared by the editor and the bulk importer. Throws {@link ValidationFailedException}. */
    @Transactional(readOnly = true)
    public Validated validate(ProductUpsert r, Long existingId, String existingSlug) {
        String sku = r.sku() == null ? "" : r.sku().trim().toUpperCase(Locale.ROOT);
        if (!SKU.matcher(sku).matches()) throw new ValidationFailedException("sku", "SKU must be 2-64 characters: letters, digits, dot, dash or underscore.");
        Optional<Product> skuClash = products.findBySku(sku);
        if (skuClash.isPresent() && !skuClash.get().getId().equals(existingId)) throw new DuplicateResourceException("sku", "That SKU is already in use.");
        String title = r.title() == null ? "" : r.title().trim();
        if (title.length() < 2 || title.length() > 200) throw new ValidationFailedException("title", "Title is required (2-200 characters).");
        if (r.description() != null && r.description().length() > 5000) throw new ValidationFailedException("description", "Description is too long (max 5000).");
        if (r.categoryId() == null) throw new ValidationFailedException("categoryId", "Choose a category.");
        Category cat = categories.findById(r.categoryId()).orElseThrow(() -> new ValidationFailedException("categoryId", "Category not found."));
        if (!cat.isActive()) throw new ValidationFailedException("categoryId", "That category is inactive.");
        BigDecimal base = price("basePrice", r.basePrice(), true);
        BigDecimal sale = price("salePrice", r.salePrice(), false);
        if (sale != null && sale.compareTo(base) > 0) throw new ValidationFailedException("salePrice", "Sale price must be between 0 and the base price.");
        String currency = r.currency() == null || r.currency().isBlank() ? settings.get("currency.code", "INR") : r.currency().trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) throw new ValidationFailedException("currency", "Use a 3-letter currency code.");
        if (r.seoTitle() != null && r.seoTitle().length() > 160) throw new ValidationFailedException("seoTitle", "SEO title is too long (max 160).");
        if (r.seoDescription() != null && r.seoDescription().length() > 320) throw new ValidationFailedException("seoDescription", "SEO description is too long (max 320).");

        String slug;
        if (r.slug() != null && !r.slug().isBlank()) {
            slug = r.slug().trim();
            if (!Slugs.isValid(slug) || RESERVED_SLUGS.contains(slug)) throw new ValidationFailedException("slug", "Slug may contain lowercase letters, digits and single hyphens.");
            Optional<Product> clash = products.findBySlug(slug);
            if (clash.isPresent() && !clash.get().getId().equals(existingId)) throw new DuplicateResourceException("slug", "That slug is already in use.");
        } else if (existingSlug != null) {
            slug = existingSlug;
        } else {
            String candidate = Slugs.slugify(title);
            if (candidate.isEmpty() || RESERVED_SLUGS.contains(candidate)) candidate = "product-" + sku.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            slug = candidate;
            int n = 2;
            while (products.existsBySlug(slug)) slug = candidate + "-" + n++;
        }
        return new Validated(sku, title, slug, blankToNull(r.description()), cat.getId(), base, sale, currency, blankToNull(r.seoTitle()), blankToNull(r.seoDescription()));
    }

    private static BigDecimal price(String field, BigDecimal v, boolean required) {
        if (v == null) {
            if (required) throw new ValidationFailedException(field, "Price is required.");
            return null;
        }
        if (v.signum() < 0) throw new ValidationFailedException(field, "Price cannot be negative.");
        if (v.compareTo(new BigDecimal("100000000")) > 0) throw new ValidationFailedException(field, "Price is too large.");
        if (v.stripTrailingZeros().scale() > 2) throw new ValidationFailedException(field, "Use at most 2 decimal places.");
        return Money.scale(v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Diff-based attribute persistence; values are checked against definitions and active lookup values. */
    void saveAttributes(Long productId, List<AttributeInput> input) {
        List<AttributeInput> in = input == null ? List.of() : input;
        Map<String, AttributeDefinition> defs = definitions.findByActiveTrueOrderBySortOrderAsc().stream().collect(Collectors.toMap(AttributeDefinition::getCode, d -> d));
        Map<String, ProductAttributeValue> existing = attributeValues.findByProduct(productId).stream().collect(Collectors.toMap(v -> v.getId().getAttributeCode(), v -> v));
        Set<String> seen = new HashSet<>();
        for (AttributeInput a : in) {
            if (a == null || a.code() == null) continue;
            AttributeDefinition d = defs.get(a.code());
            if (d == null) throw new ValidationFailedException("attributes", "Unsupported attribute '" + a.code().replaceAll("[^A-Za-z0-9_]", "?") + "'.");
            if (!seen.add(a.code())) throw new ValidationFailedException("attributes", "Attribute '" + a.code() + "' was supplied twice.");
            boolean hasValue = d.getLovGroupCode() != null ? a.valueCode() != null && !a.valueCode().isBlank() : a.textValue() != null && !a.textValue().isBlank();
            if (!hasValue) continue;   // blank = remove
            ProductAttributeValue row = existing.get(a.code());
            if (d.getLovGroupCode() != null) {
                var lov = lovs.findByGroupCodeAndValueCode(d.getLovGroupCode(), a.valueCode());
                boolean unchanged = row != null && a.valueCode().equals(row.getValueCode());
                if (lov.isEmpty() || (!lov.get().isActive() && !unchanged)) {
                    throw new ValidationFailedException("attributes", "Value '" + a.valueCode().replaceAll("[^A-Za-z0-9_]", "?") + "' is not available for " + d.getLabel() + ".");
                }
            } else if (a.textValue().length() > 200) {
                throw new ValidationFailedException("attributes", d.getLabel() + " is too long (max 200).");
            }
            if (row == null) {
                row = new ProductAttributeValue();
                row.setId(new ProductAttributeValue.Key(productId, a.code()));
            }
            row.setValueCode(d.getLovGroupCode() != null ? a.valueCode() : null);
            row.setTextValue(d.getLovGroupCode() == null ? a.textValue().trim() : null);
            attributeValues.save(row);
            existing.remove(a.code());
            seen.add(a.code());
        }
        // anything previously stored but now absent/blank is removed
        for (Map.Entry<String, ProductAttributeValue> e : existing.entrySet()) {
            AttributeInput supplied = in.stream().filter(x -> x != null && e.getKey().equals(x.code())).findFirst().orElse(null);
            boolean keep = supplied != null && ((e.getValue().getValueCode() != null && supplied.valueCode() != null && !supplied.valueCode().isBlank())
                    || (e.getValue().getTextValue() != null && supplied.textValue() != null && !supplied.textValue().isBlank()));
            if (!keep) attributeValues.delete(e.getValue());
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public AdminProduct publish(Long id) {
        Product p = load(id);
        Category c = categories.findById(p.getCategoryId()).orElseThrow();
        if (!c.isActive()) throw new BusinessRuleException("The product's category is inactive; activate it or choose another category first.");
        p.setStatus(Product.PUBLISHED);
        p.setUpdatedAt(clock.instant());
        audit.record("PRODUCT_PUBLISH", "PRODUCT", id, p.getSku());
        return view(p);
    }

    @Transactional
    public AdminProduct unpublish(Long id) {
        Product p = load(id);
        p.setStatus(Product.DRAFT);
        p.setUpdatedAt(clock.instant());
        audit.record("PRODUCT_UNPUBLISH", "PRODUCT", id, p.getSku());
        return view(p);
    }

    @Transactional
    public AdminProduct archive(Long id) {
        Product p = load(id);
        p.setStatus(Product.ARCHIVED);
        p.setUpdatedAt(clock.instant());
        audit.record("PRODUCT_ARCHIVE", "PRODUCT", id, p.getSku());
        return view(p);
    }

    // ------------------------------------------------------------------ images

    @Transactional
    public AdminProduct addImage(Long productId, byte[] bytes, String altText) {
        Product p = load(productId);
        String alt = altText == null ? "" : altText.trim();
        if (alt.length() < 3 || alt.length() > 250) throw new ValidationFailedException("altText", "Alt text is required (3-250 characters).");
        List<ProductImage> existing = images.findByProductIdOrderBySortOrderAsc(productId);
        if (existing.size() >= MAX_IMAGES) throw new BusinessRuleException("A product can have at most " + MAX_IMAGES + " images. Delete one first.");
        ImageStorageService.Stored s = storage.store(bytes);   // validates and encodes before anything is persisted
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setStorageKey(s.key());
        img.setAltText(alt);
        img.setWidth(s.width());
        img.setHeight(s.height());
        img.setSortOrder(existing.size() + 1);
        img.setPrimaryImage(existing.isEmpty());
        img.setCreatedAt(clock.instant());
        img.setUpdatedAt(clock.instant());
        images.save(img);
        imageData.save(new ProductImageData(s.key(), img.getId(), s.imageBase64(), s.thumbBase64(), clock.instant()));   // base64 payload in MySQL
        touch(p);
        audit.record("PRODUCT_IMAGE_ADD", "PRODUCT", productId, "image " + img.getSortOrder());
        return view(p);
    }

    @Transactional
    public AdminProduct reorderImages(Long productId, List<Long> imageIds) {
        Product p = load(productId);
        List<ProductImage> existing = images.findByProductIdOrderBySortOrderAsc(productId);
        if (imageIds == null || imageIds.size() != existing.size() || !new HashSet<>(imageIds).equals(existing.stream().map(ProductImage::getId).collect(Collectors.toSet()))) {
            throw new ValidationFailedException("imageIds", "Provide every image id of this product exactly once.");
        }
        Map<Long, ProductImage> byId = existing.stream().collect(Collectors.toMap(ProductImage::getId, i -> i));
        renumber(imageIds.stream().map(byId::get).toList());
        touch(p);
        audit.record("PRODUCT_IMAGE_REORDER", "PRODUCT", productId, "images reordered");
        return view(p);
    }

    @Transactional
    public AdminProduct patchImage(Long productId, Long imageId, ImagePatchRequest req) {
        Product p = load(productId);
        ProductImage img = images.findByIdAndProductId(imageId, productId).orElseThrow(() -> new ResourceNotFoundException("Image"));
        if (req.altText() != null) {
            String alt = req.altText().trim();
            if (alt.length() < 3 || alt.length() > 250) throw new ValidationFailedException("altText", "Alt text is required (3-250 characters).");
            img.setAltText(alt);
            img.setUpdatedAt(clock.instant());
        }
        if (Boolean.TRUE.equals(req.primary())) {
            List<ProductImage> ordered = new ArrayList<>(images.findByProductIdOrderBySortOrderAsc(productId));
            ordered.removeIf(i -> i.getId().equals(imageId));
            ordered.add(0, img);
            renumber(ordered);   // the primary image is always the first image
        }
        touch(p);
        return view(p);
    }

    @Transactional
    public AdminProduct deleteImage(Long productId, Long imageId) {
        Product p = load(productId);
        ProductImage img = images.findByIdAndProductId(imageId, productId).orElseThrow(() -> new ResourceNotFoundException("Image"));
        images.delete(img);
        images.flush();
        // the base64 payload row is removed by the database (ON DELETE CASCADE on product_image_data.image_id)
        renumber(images.findByProductIdOrderBySortOrderAsc(productId));
        touch(p);
        audit.record("PRODUCT_IMAGE_DELETE", "PRODUCT", productId, "image removed");
        return view(p);
    }

    private void renumber(List<ProductImage> ordered) {
        int n = 1;
        for (ProductImage i : ordered) {
            i.setSortOrder(n);
            i.setPrimaryImage(n == 1);
            i.setUpdatedAt(clock.instant());
            n++;
        }
    }

    private void touch(Product p) {
        p.setUpdatedAt(clock.instant());
    }
}
