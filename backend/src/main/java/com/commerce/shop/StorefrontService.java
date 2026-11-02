package com.commerce.shop;

import com.commerce.catalog.CatalogDtos.CategoryNode;
import com.commerce.catalog.CatalogDtos.ProductCard;
import com.commerce.catalog.CategoryService;
import com.commerce.catalog.Product;
import com.commerce.catalog.ProductViewAssembler;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.web.PublicCache;
import com.commerce.config.AppProperties;
import com.commerce.order.OrderRepositories.OrderItemRepository;
import com.commerce.shop.ShopRepositories.*;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public storefront: theme/shop config and CMS pages. Product lists inside blocks are resolved server-side from rules. */
@Service
public class StorefrontService {
    public record Theme(String bg, String surface, String text, String accent, String font) {}
    public record ShopInfo(String displayName, String tagline, String logoUrl, String supportEmail, String supportPhone, String address) {}
    public record Link(String label, String url) {}
    public record Config(Theme theme, ShopInfo shop, List<Link> headerLinks, List<Link> footerLinks, String currency, List<String> paymentMethods,
                         boolean mockPayments, String imageRatio, int cartMaxQty) {}
    public record Block(Long blockId, String type, String title, String subtitle, String body, String imageUrl, String linkUrl, String ctaLabel,
                        List<ProductCard> products, List<CategoryNode> categories) {}
    public record Page(String slug, String title, List<Block> blocks) {}

    private final SettingsService settings;
    private final ShopProfileRepository shop;
    private final SiteLinkRepository links;
    private final ContentPageRepository pages;
    private final ContentBlockRepository blocks;
    private final CategoryService categories;
    private final ProductViewAssembler assembler;
    private final OrderItemRepository orderItems;
    private final EntityManager em;
    private final PublicCache cache;
    private final AppProperties props;
    private final com.commerce.payment.PaymentProviders providers;

    public StorefrontService(SettingsService settings, ShopProfileRepository shop, SiteLinkRepository links, ContentPageRepository pages,
                             ContentBlockRepository blocks, CategoryService categories, ProductViewAssembler assembler,
                             OrderItemRepository orderItems, EntityManager em, PublicCache cache, AppProperties props,
                             com.commerce.payment.PaymentProviders providers) {
        this.settings = settings;
        this.shop = shop;
        this.links = links;
        this.pages = pages;
        this.blocks = blocks;
        this.categories = categories;
        this.assembler = assembler;
        this.orderItems = orderItems;
        this.em = em;
        this.cache = cache;
        this.props = props;
        this.providers = providers;
    }

    @Transactional(readOnly = true)
    public Config config() {
        return cache.get("storefront-config", 30, () -> {
            ShopProfile sp = shop.findById(1L).orElseGet(ShopProfile::new);
            String addr = sp.shipFromLine();
            List<Link> header = new ArrayList<>(), footer = new ArrayList<>();
            for (SiteLink l : links.findByActiveTrueOrderBySortOrderAscIdAsc()) {
                (("HEADER".equals(l.getPlacement())) ? header : footer).add(new Link(l.getLabel(), l.getUrl()));
            }
            List<String> methods = new ArrayList<>();
            if (settings.getBool("payments.cod.enabled", true)) methods.add("COD");
            boolean online = settings.getBool("payments.online.enabled", false) && providers.online().isPresent();
            if (online) methods.add("ONLINE");
            boolean mock = online && providers.online().map(p -> p.mock()).orElse(false);
            return new Config(new Theme(settings.get("theme.bg", "#FAF6EE"), settings.get("theme.surface", "#FFFFFF"), settings.get("theme.text", "#2B2622"),
                    settings.get("theme.accent", "#9C7A3C"), settings.get("theme.font", "SERIF")),
                    new ShopInfo(sp.getDisplayName() == null ? "Jewel Store" : sp.getDisplayName(), sp.getTagline(), sp.getLogoUrl(), sp.getSupportEmail(),
                            sp.getSupportPhone(), addr.isEmpty() ? null : addr),
                    header, footer, settings.get("currency.code", "INR"), methods, mock, settings.get("media.aspectRatio", "1:1"),
                    settings.getInt("cart.maxQty", 10));
        });
    }

    @Transactional(readOnly = true)
    public Page page(String slug) {
        ContentPage p = pages.findBySlug(slug).filter(ContentPage::isPublished).orElseThrow(() -> new ResourceNotFoundException("Page"));
        List<Block> out = new ArrayList<>();
        for (ContentBlock b : blocks.findByPageIdOrderBySortOrderAscIdAsc(p.getId())) {
            if (!b.isPublished()) continue;
            out.add(assemble(b));
        }
        return new Page(p.getSlug(), p.getTitle(), out);
    }

    private Block assemble(ContentBlock b) {
        List<ProductCard> products = null;
        List<CategoryNode> cats = null;
        int limit = b.getItemLimit() == null ? 8 : Math.max(1, Math.min(b.getItemLimit(), 24));
        if ("FEATURED_PRODUCTS".equals(b.getBlockType())) products = assembler.cards(productsFor(b.getProductRule(), b.getCategoryId(), limit));
        if ("CATEGORY_TILES".equals(b.getBlockType())) {
            List<CategoryNode> tree = categories.publicTree();
            cats = tree.size() > limit ? tree.subList(0, limit) : tree;
        }
        return new Block(b.getId(), b.getBlockType(), b.getTitle(), b.getSubtitle(), b.getBody(), b.getImageUrl(), b.getLinkUrl(), b.getCtaLabel(), products, cats);
    }

    private static final String LIVE = "p.status = 'PUBLISHED' and exists (select 1 from Category c where c.id = p.categoryId and c.active = true)";

    List<Product> productsFor(String rule, Long categoryId, int limit) {
        String r = rule == null ? "NEWEST" : rule;
        switch (r) {
            case "BEST_SELLERS" -> {
                List<Long> ids = orderItems.bestSellerIds(PageRequest.of(0, limit * 3));
                if (ids.isEmpty()) return productsFor("NEWEST", null, limit);
                Map<Long, Product> byId = new LinkedHashMap<>();
                em.createQuery("select p from Product p where p.id in :ids and " + LIVE, Product.class).setParameter("ids", ids).getResultList()
                        .forEach(p -> byId.put(p.getId(), p));
                List<Product> ordered = new ArrayList<>();
                for (Long id : ids) { Product p = byId.get(id); if (p != null && ordered.size() < limit) ordered.add(p); }
                return ordered;
            }
            case "FEATURED" -> {
                return em.createQuery("select p from Product p where p.featured = true and " + LIVE + " order by p.createdAt desc, p.id desc", Product.class)
                        .setMaxResults(limit).getResultList();
            }
            case "CATEGORY" -> {
                if (categoryId == null) return List.of();
                var ids = categories.subtreeIds(categoryId);
                if (ids.isEmpty()) return List.of();
                return em.createQuery("select p from Product p where p.categoryId in :cats and " + LIVE + " order by p.createdAt desc, p.id desc", Product.class)
                        .setParameter("cats", ids).setMaxResults(limit).getResultList();
            }
            default -> {
                return em.createQuery("select p from Product p where " + LIVE + " order by p.createdAt desc, p.id desc", Product.class)
                        .setMaxResults(limit).getResultList();
            }
        }
    }
}
