package com.commerce.shop;

import jakarta.persistence.*;

@Entity
@Table(name = "site_links")
public class SiteLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;
    private String placement;
    private String label;
    private String url;
    private int sortOrder;
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlacement() { return placement; }
    public void setPlacement(String placement) { this.placement = placement; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
