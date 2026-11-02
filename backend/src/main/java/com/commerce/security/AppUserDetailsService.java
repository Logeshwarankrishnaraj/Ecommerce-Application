package com.commerce.security;

import com.commerce.user.AppUser;
import com.commerce.user.AppUserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        String key = usernameOrEmail == null ? "" : usernameOrEmail.trim().toLowerCase(Locale.ROOT);
        AppUser u = users.findByUsernameOrEmail(key).orElseThrow(() -> new UsernameNotFoundException("not found"));
        return UserPrincipal.of(u);
    }
}
