package com.commerce.shop;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "site_settings")
public class SiteSetting {
    @Id
    @Column(name = "setting_key")
    private String key;
    @Column(name = "setting_value")
    private String value;
    @Column(name = "group_code")
    private String group;
    @Column(name = "value_type")
    private String type;
    private String description;
    private String options;
    private String updatedBy;
    private Instant updatedAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
