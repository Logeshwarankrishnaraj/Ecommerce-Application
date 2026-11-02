package com.commerce.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MOCK - no real money is processed. Deterministic outcomes are set through the dev-only endpoint.
 * Only instantiated when app.payment.mock-enabled=true, which the startup safety check forbids outside local/test.
 */
@Component
@ConditionalOnProperty(name = "app.payment.mock-enabled", havingValue = "true")
public class MockPaymentProvider implements PaymentProvider {
    private final Map<String, Outcome> outcomes = new ConcurrentHashMap<>();

    @Override public String code() { return "mock"; }

    @Override public boolean mock() { return true; }

    @Override
    public Initiation initiate(Long orderId, BigDecimal amount, String currency) {
        String ref = "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        outcomes.put(ref, Outcome.INITIATED);
        return new Initiation(ref, true);
    }

    @Override
    public Outcome verify(String providerReference) {
        return outcomes.getOrDefault(providerReference, Outcome.INITIATED);
    }

    @Override
    public boolean refund(String providerReference, BigDecimal amount) {
        return outcomes.get(providerReference) == Outcome.PAID;
    }

    /** Test hook (called by the dev controller). */
    public void simulate(String providerReference, Outcome outcome) {
        outcomes.put(providerReference, outcome);
    }
}
