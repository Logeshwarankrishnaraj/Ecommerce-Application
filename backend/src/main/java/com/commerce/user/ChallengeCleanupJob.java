package com.commerce.user;

import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retention: remove registration / reset challenges older than 7 days (contains personal data). */
@Component
public class ChallengeCleanupJob {
    private final RegistrationChallengeRepository reg;
    private final PasswordResetChallengeRepository reset;
    private final Clock clock;

    public ChallengeCleanupJob(RegistrationChallengeRepository reg, PasswordResetChallengeRepository reset, Clock clock) {
        this.reg = reg;
        this.reset = reset;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(7));
        reg.deleteOlderThan(cutoff);
        reset.deleteOlderThan(cutoff);
    }
}
