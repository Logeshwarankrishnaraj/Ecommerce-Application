package com.commerce.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.commerce.config.AppProperties;

/**
 * DEMO ACCOUNTS - local/test profiles ONLY (SRS section 15). Passwords are hashed with the configured encoder.
 * Do not reuse these credentials anywhere else.
 */
@Component
@Profile({"local", "test"})
public class DevDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Clock clock;

    public DevDataSeeder(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, TransactionTemplate tx,
                         AppProperties props, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (!props.getSeed().isDemoAccounts()) return;
        tx.executeWithoutResult(s -> {
            seed("admin", "Demo Admin", "admin@jewelstore.local", "9000000001", "Admin@12345!", Role.ADMIN, Role.SUPER_ADMIN);
            seed("customer", "Demo Customer", "customer@jewelstore.local", "9000000002", "Customer@12345!", Role.CUSTOMER);
        });
        log.warn("DEMO ACCOUNTS are enabled (local/test only): admin@jewelstore.local / customer@jewelstore.local");
    }

    private void seed(String username, String name, String email, String mobile, String password, String... roleCodes) {
        if (users.existsByEmail(email)) return;
        Instant now = clock.instant();
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setFullName(name);
        u.setEmail(email);
        u.setMobileNumber(mobile);
        u.setPasswordHash(encoder.encode(password));
        u.setEmailVerifiedAt(now);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        for (String code : roleCodes) {
            u.getRoles().add(roles.findByRoleCode(code).orElseThrow());
        }
        users.save(u);
    }
}
