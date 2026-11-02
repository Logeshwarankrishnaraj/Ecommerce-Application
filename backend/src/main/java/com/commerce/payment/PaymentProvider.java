package com.commerce.payment;

import java.math.BigDecimal;

/** Abstraction over online payment gateways (SRS 7.4). Real providers plug in here; the app ships only a dev mock. */
public interface PaymentProvider {
    enum Outcome { INITIATED, PAID, FAILED, CANCELLED }

    record Initiation(String providerReference, boolean requiresAction) {}

    String code();

    /** True for providers that move no real money. */
    boolean mock();

    Initiation initiate(Long orderId, BigDecimal amount, String currency);

    /** Server-to-provider status lookup; the browser's claim is never trusted. */
    Outcome verify(String providerReference);

    /** Returns true when the refund was accepted. */
    boolean refund(String providerReference, BigDecimal amount);
}
