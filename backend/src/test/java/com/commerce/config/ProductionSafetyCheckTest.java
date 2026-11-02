package com.commerce.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SYS-08: production-like profiles must refuse demo seeding and mock payments. */
class ProductionSafetyCheckTest {

    private AppProperties props(boolean seed, boolean mock, boolean logOnly) {
        AppProperties p = new AppProperties();
        p.getSeed().setDemoAccounts(seed);
        p.getPayment().setMockEnabled(mock);
        p.getMail().setLogOnly(logOnly);
        return p;
    }

    @Test
    void prodRejectsDemoAccounts() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(List.of("prod"), props(true, false, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodRejectsMockPayments() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(List.of(), props(false, true, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodRejectsLogOnlyMail() {
        assertThatThrownBy(() -> ProductionSafetyCheck.validate(List.of("prod"), props(false, false, true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void localAllowsEverything() {
        assertThatCode(() -> ProductionSafetyCheck.validate(List.of("local"), props(true, true, true))).doesNotThrowAnyException();
    }
}
