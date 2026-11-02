package com.commerce.user;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    public static final String CUSTOMER = "ROLE_CUSTOMER";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;
    @Column(name = "role_code")
    private String roleCode;
    private String displayName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
