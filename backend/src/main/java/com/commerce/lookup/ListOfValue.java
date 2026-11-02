package com.commerce.lookup;

import jakarta.persistence.*;

@Entity
@Table(name = "list_of_values")
public class ListOfValue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lov_id")
    private Long id;
    private String groupCode;
    private String valueCode;
    private String displayLabel;
    private boolean active = true;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getValueCode() { return valueCode; }
    public void setValueCode(String valueCode) { this.valueCode = valueCode; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
