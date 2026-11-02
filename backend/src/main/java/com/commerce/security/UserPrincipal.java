package com.commerce.security;

import com.commerce.user.AppUser;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal stored in the HTTP session. Never carries the password hash. */
public class UserPrincipal implements UserDetails, Serializable {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String username;
    private final String email;
    private final Set<String> roles;

    public UserPrincipal(Long userId, String username, String email, Set<String> roles) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.roles = Set.copyOf(roles);
    }

    public static UserPrincipal of(AppUser u) {
        return new UserPrincipal(u.getId(), u.getUsername(), u.getEmail(), u.roleCodes());
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public Set<String> getRoles() { return roles; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList();
    }
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
