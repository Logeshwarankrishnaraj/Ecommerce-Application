package com.commerce.shop;

import jakarta.persistence.*;

@Entity
@Table(name = "content_blocks")
public class ContentBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long id;
    private Long pageId;
    private String blockType;
    private String title;
    private String subtitle;
    @Column(columnDefinition = "TEXT")
    private String body;
    private String imageUrl;
    private String linkUrl;
    private String ctaLabel;
    private String productRule;
    private Long categoryId;
    private Integer itemLimit;
    private int sortOrder;
    private boolean published = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }
    public String getBlockType() { return blockType; }
    public void setBlockType(String blockType) { this.blockType = blockType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public String getCtaLabel() { return ctaLabel; }
    public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
    public String getProductRule() { return productRule; }
    public void setProductRule(String productRule) { this.productRule = productRule; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getItemLimit() { return itemLimit; }
    public void setItemLimit(Integer itemLimit) { this.itemLimit = itemLimit; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}
