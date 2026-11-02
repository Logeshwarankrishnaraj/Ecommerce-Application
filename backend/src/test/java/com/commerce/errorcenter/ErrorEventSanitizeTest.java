package com.commerce.errorcenter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ERR-05: nothing sensitive may reach stored events or alert emails. */
class ErrorEventSanitizeTest {

    @Test
    void redactsSecretsEmailsAndLongNumbers() {
        String out = ErrorEventService.sanitize("failed password=Hunter2! otp: 123456 for bob@example.com card 4111111111111111");
        assertThat(out).doesNotContain("Hunter2", "123456", "bob@example.com", "4111111111111111");
        assertThat(out).contains("[REDACTED]", "[email]", "[num]");
    }

    @Test
    void truncatesAndFlattensNewlines() {
        String out = ErrorEventService.sanitize("a\nb".repeat(500));
        assertThat(out.length()).isLessThanOrEqualTo(300);
        assertThat(out).doesNotContain("\n");
    }

    @Test
    void nullIsEmpty() {
        assertThat(ErrorEventService.sanitize(null)).isEmpty();
    }
}
