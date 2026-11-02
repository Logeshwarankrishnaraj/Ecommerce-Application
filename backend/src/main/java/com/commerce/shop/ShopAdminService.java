package com.commerce.shop;

import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogRepositories.CategoryRepository;
import com.commerce.common.exception.DuplicateResourceException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.util.Slugs;
import com.commerce.common.web.PublicCache;
import com.commerce.shop.ShopRepositories.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin editing of shop profile, header/footer links and CMS pages. Plain text only; URLs are restricted to safe schemes. */
@Service
public class ShopAdminService {
    public record ProfileDto(String displayName, String tagline, String logoUrl, String supportEmail, String supportPhone, String senderDisplayName,
                             String addressLine1, String addressLine2, String city, String state, String postalCode, String country,
                             String returnAddress, Integer handlingTimeDays) {}
    public record LinkDto(String placement, String label, String url, Integer sortOrder, Boolean active) {}
    public record BlockDto(Long blockId, String type, String title, String subtitle, String body, String imageUrl, String linkUrl, String ctaLabel,
                           String productRule, Long categoryId, Integer itemLimit, Integer sortOrder, Boolean published) {}
    public record PageDto(Long pageId, String slug, String title, boolean published, List<BlockDto> blocks) {}
    public record PageCreate(String slug, String title, Boolean published) {}
    public record PagePatch(String title, Boolean published, List<BlockDto> blocks) {}

    private static final Set<String> BLOCK_TYPES = Set.of("HERO", "CATEGORY_TILES", "FEATURED_PRODUCTS", "PROMO_STRIP", "TEXT_IMAGE");
    private static final Set<String> RULES = Set.of("NEWEST", "FEATURED", "BEST_SELLERS", "CATEGORY");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ShopProfileRepository profiles;
    private final SiteLinkRepository links;
    private final ContentPageRepository pages;
    private final ContentBlockRepository blocks;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final PublicCache cache;
    private final Clock clock;

    public ShopAdminService(ShopProfileRepository profiles, SiteLinkRepository links, ContentPageRepository pages, ContentBlockRepository blocks,
                            CategoryRepository categories, AuditService audit, PublicCache cache, Clock clock) {
        this.profiles = profiles;
        this.links = links;
        this.pages = pages;
        this.blocks = blocks;
        this.categories = categories;
        this.audit = audit;
        this.cache = cache;
        this.clock = clock;
    }

    // ---- profile
    @Transactional(readOnly = true)
    public ProfileDto profile() {
        return toDto(profiles.findById(1L).orElseGet(ShopProfile::new));
    }

    @Transactional
    public ProfileDto saveProfile(ProfileDto d) {
        ShopProfile p = profiles.findById(1L).orElseGet(ShopProfile::new);
        p.setId(1L);
        p.setDisplayName(required(d.displayName(), "displayName", 120));
        p.setTagline(text(d.tagline(), "tagline", 200));
        p.setLogoUrl(url(d.logoUrl(), "logoUrl", 300));
        String email = text(d.supportEmail(), "supportEmail", 254);
        if (email != null && !EMAIL.matcher(email).matches()) throw new ValidationFailedException("supportEmail", "Enter a valid email address.");
        p.setSupportEmail(email);
        p.setSupportPhone(text(d.supportPhone(), "supportPhone", 30));
        p.setSenderDisplayName(text(d.senderDisplayName(), "senderDisplayName", 120));
        p.setAddressLine1(text(d.addressLine1(), "addressLine1", 200));
        p.setAddressLine2(text(d.addressLine2(), "addressLine2", 200));
        p.setCity(text(d.city(), "city", 100));
        p.setState(text(d.state(), "state", 100));
        p.setPostalCode(text(d.postalCode(), "postalCode", 12));
        p.setCountry(text(d.country(), "country", 60));
        p.setReturnAddress(text(d.returnAddress(), "returnAddress", 500));
        int days = d.handlingTimeDays() == null ? 2 : d.handlingTimeDays();
        if (days < 0 || days > 60) throw new ValidationFailedException("handlingTimeDays", "Enter a number between 0 and 60.");
        p.setHandlingTimeDays(days);
        p.setUpdatedAt(clock.instant());
        profiles.save(p);
        cache.clear();
        audit.record("SHOP_PROFILE_UPDATE", "SHOP", 1, "profile updated");
        return toDto(p);
    }

    private static ProfileDto toDto(ShopProfile p) {
        return new ProfileDto(p.getDisplayName(), p.getTagline(), p.getLogoUrl(), p.getSupportEmail(), p.getSupportPhone(), p.getSenderDisplayName(),
                p.getAddressLine1(), p.getAddressLine2(), p.getCity(), p.getState(), p.getPostalCode(), p.getCountry(), p.getReturnAddress(), p.getHandlingTimeDays());
    }

    // ---- links
    @Transactional(readOnly = true)
    public List<LinkDto> links() {
        return links.findAllByOrderByPlacementAscSortOrderAscIdAsc().stream()
                .map(l -> new LinkDto(l.getPlacement(), l.getLabel(), l.getUrl(), l.getSortOrder(), l.isActive())).toList();
    }

    @Transactional
    public List<LinkDto> saveLinks(List<LinkDto> in) {
        if (in == null) throw new ValidationFailedException("links", "A list is required.");
        if (in.size() > 30) throw new ValidationFailedException("links", "At most 30 links.");
        List<SiteLink> fresh = new ArrayList<>();
        int i = 0;
        for (LinkDto d : in) {
            i++;
            if (d.placement() == null || !Set.of("HEADER", "FOOTER").contains(d.placement())) throw new ValidationFailedException("placement", "Placement must be HEADER or FOOTER.");
            SiteLink l = new SiteLink();
            l.setPlacement(d.placement());
            l.setLabel(required(d.label(), "label", 60));
            l.setUrl(required(url(d.url(), "url", 300), "url", 300));
            l.setSortOrder(d.sortOrder() == null ? i : d.sortOrder());
            l.setActive(d.active() == null || d.active());
            fresh.add(l);
        }
        links.deleteAllInBatch();
        links.saveAll(fresh);
        cache.clear();
        audit.record("SITE_LINKS_UPDATE", "SITE_LINKS", "all", fresh.size() + " links");
        return links();
    }

    // ---- pages
    @Transactional(readOnly = true)
    public List<PageDto> pages() {
        return pages.findAllByOrderByIdAsc().stream().map(this::pageDto).toList();
    }

    @Transactional
    public PageDto createPage(PageCreate req) {
        required(req.title(), "title", 160);
        String slug = req.slug() == null || req.slug().isBlank() ? Slugs.slugify(req.title()) : req.slug().trim().toLowerCase();
        if (!Slugs.isValid(slug)) throw new ValidationFailedException("slug", "Use lowercase letters, numbers and hyphens.");
        if (pages.existsBySlug(slug)) throw new DuplicateResourceException("A page with this slug already exists.");
        ContentPage p = new ContentPage();
        p.setSlug(slug);
        p.setTitle(required(req.title(), "title", 160));
        p.setPublished(req.published() != null && req.published());
        p.setCreatedAt(clock.instant());
        p.setUpdatedAt(clock.instant());
        pages.save(p);
        cache.clear();
        audit.record("PAGE_CREATE", "PAGE", slug, "created");
        return pageDto(p);
    }

    @Transactional
    public PageDto updatePage(Long id, PagePatch req) {
        ContentPage p = pages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Page"));
        if (req.title() != null) p.setTitle(required(req.title(), "title", 160));
        if (req.published() != null) {
            if ("home".equals(p.getSlug()) && !req.published()) throw new ValidationFailedException("published", "The home page must stay published.");
            p.setPublished(req.published());
        }
        if (req.blocks() != null) {
            if (req.blocks().size() > 40) throw new ValidationFailedException("blocks", "At most 40 blocks per page.");
            List<ContentBlock> fresh = new ArrayList<>();
            int i = 0;
            for (BlockDto d : req.blocks()) { i++; fresh.add(toBlock(p.getId(), d, i)); }
            blocks.deleteByPageId(p.getId());
            blocks.flush();
            blocks.saveAll(fresh);
        }
        p.setUpdatedAt(clock.instant());
        cache.clear();
        audit.record("PAGE_UPDATE", "PAGE", p.getSlug(), "updated");
        return pageDto(p);
    }

    private ContentBlock toBlock(Long pageId, BlockDto d, int position) {
        if (d.type() == null || !BLOCK_TYPES.contains(d.type())) throw new ValidationFailedException("type", "Unknown block type.");
        ContentBlock b = new ContentBlock();
        b.setPageId(pageId);
        b.setBlockType(d.type());
        b.setTitle(text(d.title(), "title", 200));
        b.setSubtitle(text(d.subtitle(), "subtitle", 300));
        b.setBody(text(d.body(), "body", 8000));
        b.setImageUrl(url(d.imageUrl(), "imageUrl", 300));
        b.setLinkUrl(url(d.linkUrl(), "linkUrl", 300));
        b.setCtaLabel(text(d.ctaLabel(), "ctaLabel", 60));
        if ("FEATURED_PRODUCTS".equals(d.type())) {
            String rule = d.productRule() == null ? "NEWEST" : d.productRule();
            if (!RULES.contains(rule)) throw new ValidationFailedException("productRule", "Unknown product rule.");
            b.setProductRule(rule);
            if ("CATEGORY".equals(rule)) {
                if (d.categoryId() == null || !categories.existsById(d.categoryId())) throw new ValidationFailedException("categoryId", "Choose a category.");
                b.setCategoryId(d.categoryId());
            }
        }
        if (d.itemLimit() != null) {
            if (d.itemLimit() < 1 || d.itemLimit() > 24) throw new ValidationFailedException("itemLimit", "Use a number between 1 and 24.");
            b.setItemLimit(d.itemLimit());
        }
        b.setSortOrder(d.sortOrder() == null ? position : d.sortOrder());
        b.setPublished(d.published() == null || d.published());
        return b;
    }

    private PageDto pageDto(ContentPage p) {
        List<BlockDto> bl = blocks.findByPageIdOrderBySortOrderAscIdAsc(p.getId()).stream().map(b -> new BlockDto(b.getId(), b.getBlockType(), b.getTitle(),
                b.getSubtitle(), b.getBody(), b.getImageUrl(), b.getLinkUrl(), b.getCtaLabel(), b.getProductRule(), b.getCategoryId(), b.getItemLimit(),
                b.getSortOrder(), b.isPublished())).toList();
        return new PageDto(p.getId(), p.getSlug(), p.getTitle(), p.isPublished(), bl);
    }

    // ---- validation helpers
    static String text(String s, String field, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.indexOf('<') >= 0 || t.indexOf('>') >= 0) throw new ValidationFailedException(field, "HTML is not allowed; use plain text.");
        if (t.length() > max) throw new ValidationFailedException(field, "Must be at most " + max + " characters.");
        return t;
    }

    static String required(String s, String field, int max) {
        String t = text(s, field, max);
        if (t == null) throw new ValidationFailedException(field, "This field is required.");
        return t;
    }

    /** Relative paths ("/products") or http(s) URLs only: blocks javascript: and data: schemes. */
    static String url(String s, String field, int max) {
        String t = text(s, field, max);
        if (t == null) return null;
        boolean ok = (t.startsWith("/") && !t.startsWith("//")) || t.toLowerCase().startsWith("https://") || t.toLowerCase().startsWith("http://");
        if (!ok) throw new ValidationFailedException(field, "Use a path starting with / or an http(s) URL.");
        return t;
    }
}
