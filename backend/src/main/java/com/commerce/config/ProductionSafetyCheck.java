package com.commerce.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * SRS 0.11 / 15: refuse to start with demo accounts or mock payments unless a local/test profile is active.
 */
@Component
public class ProductionSafetyCheck {
    private static final Set<String> DEV_PROFILES = Set.of("local", "test");
    private final AppProperties props;
    private final Environment env;

    public ProductionSafetyCheck(AppProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        validate(Arrays.asList(env.getActiveProfiles()), props);
    }

    /** Pure function so it can be unit tested. */
    public static void validate(Iterable<String> activeProfiles, AppProperties props) {
        boolean dev = false;
        for (String p : activeProfiles) {
            if (DEV_PROFILES.contains(p)) dev = true;
        }
        if (!dev && props.getSeed().isDemoAccounts()) {
            throw new IllegalStateException("app.seed.demo-accounts must be false outside local/test profiles");
        }
        if (!dev && (props.getPayment().isMockEnabled() || "mock".equalsIgnoreCase(props.getPayment().getMode()))) {
            throw new IllegalStateException("Mock payments must not be enabled outside local/test profiles");
        }
        if (!dev && props.getMail().isLogOnly()) {
            throw new IllegalStateException("app.mail.log-only must be false outside local/test profiles");
        }
    }
}
