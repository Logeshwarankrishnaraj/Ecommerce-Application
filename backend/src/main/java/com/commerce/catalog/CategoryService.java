package com.commerce.catalog;

import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogDtos.*;
import com.commerce.catalog.CatalogRepositories.*;
import com.commerce.common.exception.*;
import com.commerce.common.util.Slugs;
import com.commerce.common.web.PublicCache;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final AuditService audit;
    private final PublicCache cache;
    private final Clock clock;

    public CategoryService(CategoryRepository categories, ProductRepository products, AuditService audit, PublicCache cache, Clock clock) {
        this.categories = categories;
        this.products = products;
        this.audit = audit;
        this.cache = cache;
        this.clock = clock;
    }

    /** Public tree of active categories (a child under an inactive parent is hidden). */
    @Transactional(readOnly = true)
    public List<CategoryNode> publicTree() {
        return cache.get("categories:tree", 30, () -> buildTree(categories.findByActiveTrueOrderBySortOrderAscNameAsc()));
    }

    static List<CategoryNode> buildTree(List<Category> active) {
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : active) byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        return children(null, byParent);
    }

    private static List<CategoryNode> children(Long parent, Map<Long, List<Category>> byParent) {
        List<CategoryNode> out = new ArrayList<>();
        for (Category c : byParent.getOrDefault(parent, List.of())) {
            out.add(new CategoryNode(c.getId(), c.getName(), c.getSlug(), c.getDescription(), children(c.getId(), byParent)));
        }
        return out;
    }

    /** The category plus all descendants (ids), for category-page filtering. */
    @Transactional(readOnly = true)
    public Set<Long> subtreeIds(Long rootId) {
        List<Category> all = categories.findByActiveTrueOrderBySortOrderAscNameAsc();
        Set<Long> out = new HashSet<>(Set.of(rootId));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Category c : all) if (c.getParentId() != null && out.contains(c.getParentId()) && out.add(c.getId())) grew = true;
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AdminCategory> adminList() {
        return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(this::adminRow).toList();
    }

    @Transactional
    public AdminCategory create(CategoryUpsert req) {
        Category c = new Category();
        c.setCreatedAt(clock.instant());
        apply(c, req, true);
        c = categories.save(c);
        audit.record("CATEGORY_CREATE", "CATEGORY", c.getId(), c.getName());
        cache.clear();
        return adminRow(c);
    }

    @Transactional
    public AdminCategory update(Long id, CategoryUpsert req) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category"));
        boolean wasActive = c.isActive();
        apply(c, req, false);
        if (wasActive && !c.isActive() && products.countByCategoryIdAndStatus(id, Product.PUBLISHED) > 0) {
            throw new BusinessRuleException("This category has published products. Unpublish or move them before deactivating it.");
        }
        audit.record("CATEGORY_UPDATE", "CATEGORY", id, c.getName() + " active=" + c.isActive());
        cache.clear();
        return adminRow(c);
    }

    @Transactional
    public void delete(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category"));
        if (products.countByCategoryId(id) > 0) throw new ConflictException("This category still has products. Move them first or deactivate the category.");
        if (categories.countByParentId(id) > 0) throw new ConflictException("This category has child categories. Remove or move them first.");
        categories.delete(c);
        audit.record("CATEGORY_DELETE", "CATEGORY", id, c.getName());
        cache.clear();
    }

    private void apply(Category c, CategoryUpsert r, boolean creating) {
        if (r.name() == null || r.name().isBlank() || r.name().length() > 100) throw new ValidationFailedException("name", "Name is required (max 100 characters).");
        if (r.description() != null && r.description().length() > 500) throw new ValidationFailedException("description", "Description is too long.");
        String slug = r.slug() == null || r.slug().isBlank() ? Slugs.slugify(r.name()) : r.slug().trim();
        if (!Slugs.isValid(slug)) throw new ValidationFailedException("slug", "Slug may contain lowercase letters, digits and single hyphens.");
        Optional<Category> clash = categories.findBySlug(slug);
        if (clash.isPresent() && !clash.get().getId().equals(c.getId())) throw new DuplicateResourceException("slug", "That slug is already used by another category.");
        if (r.parentCategoryId() != null) {
            Category parent = categories.findById(r.parentCategoryId()).orElseThrow(() -> new ValidationFailedException("parentCategoryId", "Parent category not found."));
            if (!creating && wouldCycle(c.getId(), parent)) throw new ValidationFailedException("parentCategoryId", "A category cannot be moved under itself or its own descendant.");
        }
        c.setParentId(r.parentCategoryId());
        c.setName(r.name().trim());
        c.setSlug(slug);
        c.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        c.setActive(r.active() == null || r.active());
        c.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        c.setUpdatedAt(clock.instant());
    }

    private boolean wouldCycle(Long id, Category newParent) {
        Category cur = newParent;
        Set<Long> seen = new HashSet<>();
        while (cur != null && seen.add(cur.getId())) {
            if (cur.getId().equals(id)) return true;
            cur = cur.getParentId() == null ? null : categories.findById(cur.getParentId()).orElse(null);
        }
        return false;
    }

    private AdminCategory adminRow(Category c) {
        return new AdminCategory(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getDescription() == null ? "" : c.getDescription(),
                c.isActive(), c.getSortOrder(), products.countByCategoryId(c.getId()));
    }
}
