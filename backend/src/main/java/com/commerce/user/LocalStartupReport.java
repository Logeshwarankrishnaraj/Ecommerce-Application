package com.commerce.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** LOCAL/TEST ONLY: prints a one-line proof of the database connection and seeded demo accounts at startup. */
@Component
@Profile({"local", "test"})
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalStartupReport implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalStartupReport.class);
    private final AppUserRepository users;

    public LocalStartupReport(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        long n = users.count();
        boolean admin = users.existsByEmail("admin@jewelstore.local");
        log.info("LOCAL CHECK: database reachable, app_users={}, demo admin present={}. "
                + "Open http://localhost:8080/api/v1/dev/ping or http://localhost:4200/diagnostics to test the connection.", n, admin);
    }
}
