package com.commerce.user;

import com.commerce.config.AppProperties;
import com.commerce.security.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first SUPER_ADMIN on an empty production database from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD.
 * Does nothing once any super admin exists. Remove the variables after the first start.
 */
@Component
public class BootstrapAdminRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private final AppProperties props;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BootstrapAdminRunner(AppProperties props, AppUserRepository users, RoleRepository roles, PasswordEncoder encoder, TransactionTemplate tx, Clock clock) {
        this.props = props;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        String email = props.getSeed().getBootstrapAdminEmail();
        String password = props.getSeed().getBootstrapAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;
        String violation = PasswordPolicy.violation(password);
        if (violation != null) throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD rejected: " + violation);
        tx.executeWithoutResult(s -> {
            Role superAdmin = roles.findByRoleCode(Role.SUPER_ADMIN).orElseThrow();
            if (users.findAll().stream().anyMatch(u -> u.roleCodes().contains(Role.SUPER_ADMIN))) return;
            if (users.existsByEmail(email.trim())) throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL belongs to an existing non-admin account.");
            Instant now = clock.instant();
            AppUser u = new AppUser();
            u.setUsername("admin");
            u.setFullName("Administrator");
            u.setEmail(email.trim());
            u.setMobileNumber("0000000000");
            u.setPasswordHash(encoder.encode(password));
            u.setEmailVerifiedAt(now);
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            u.getRoles().add(superAdmin);
            users.save(u);
            log.warn("Bootstrap super admin created for {}. Remove BOOTSTRAP_ADMIN_* from the environment now.", email.trim());
        });
    }
}
