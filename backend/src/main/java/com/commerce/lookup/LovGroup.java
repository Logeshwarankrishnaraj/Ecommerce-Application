package com.commerce.lookup;

import jakarta.persistence.*;

@Entity
@Table(name = "lov_groups")
public class LovGroup {
    @Id
    @Column(name = "group_code")
    private String groupCode;
    private String label;
    @Column(name = "public_group")
    private boolean publicGroup = true;

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isPublicGroup() { return publicGroup; }
    public void setPublicGroup(boolean publicGroup) { this.publicGroup = publicGroup; }
}
