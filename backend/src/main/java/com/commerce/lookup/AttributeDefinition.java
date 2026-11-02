package com.commerce.lookup;

import jakarta.persistence.*;

@Entity
@Table(name = "product_attribute_definitions")
public class AttributeDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attribute_id")
    private Long id;
    private String code;
    private String label;
    private String lovGroupCode;
    private boolean filterable = true;
    private boolean active = true;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getLovGroupCode() { return lovGroupCode; }
    public void setLovGroupCode(String lovGroupCode) { this.lovGroupCode = lovGroupCode; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
