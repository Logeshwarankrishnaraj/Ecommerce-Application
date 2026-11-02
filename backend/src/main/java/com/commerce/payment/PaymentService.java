package com.commerce.payment;

import com.commerce.cart.CartService;
import com.commerce.common.exception.*;
import com.commerce.common.util.Hashing;
import com.commerce.config.AppProperties;
import com.commerce.order.CustomerOrder;
import com.commerce.order.OrderLifecycle;
import com.commerce.order.OrderRepositories.OrderRepository;
import com.commerce.order.OrderStateMachine;
import com.commerce.security.CurrentUser;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies payments server-side. A payment becomes PAID only when the provider says so (direct verify call or a
 * signed webhook); the browser's redirect/claim is never trusted. All transitions are idempotent (SHOP-09/10).
 */
@Service
public class PaymentService {
    public record Verification(Long paymentId, String status, Long orderId, String orderNumber, String orderStatus) {}

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderLifecycle lifecycle;
    private final PaymentProviders providers;
    private final CartService cart;
    private final AppProperties props;
    private final EntityManager em;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderLifecycle lifecycle, PaymentProviders providers,
                          CartService cart, AppProperties props, EntityManager em, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.providers = providers;
        this.cart = cart;
        this.props = props;
        this.em = em;
        this.clock = clock;
    }

    /** Customer-initiated verification: asks the provider, then applies the result. Ownership enforced. */
    @Transactional
    public Verification verify(Long paymentId) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        CustomerOrder o = orders.findByIdAndUserId(p.getOrderId(), CurrentUser.requireId()).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        if (Payment.INITIATED.equals(p.getStatus())) {
            PaymentProvider provider = providers.byCode(p.getProvider()).orElseThrow(() -> new BusinessRuleException("The payment provider is unavailable."));
            apply(p, provider.verify(p.getProviderReference()));
        }
        return new Verification(p.getId(), p.getStatus(), o.getId(), o.getOrderNumber(), o.getStatus());
    }

    /** Applies a provider outcome exactly once. */
    @Transactional
    public void apply(Payment p, PaymentProvider.Outcome outcome) {
        if (!Payment.INITIATED.equals(p.getStatus())) return;   // already settled -> idempotent no-op
        CustomerOrder o = orders.findById(p.getOrderId()).orElseThrow();
        switch (outcome) {
            case PAID -> {
                if (OrderStateMachine.PENDING_PAYMENT.equals(o.getStatus())) {
                    p.setStatus(Payment.PAID);
                    o.setPaymentStatus(Payment.PAID);
                    lifecycle.commitReservations(o);
                    lifecycle.record(o, OrderStateMachine.PLACED, "Payment verified", "payment-provider");
                    cart.clearFor(o.getUserId());
                } else {
                    // Money arrived for an order that was already cancelled/expired: refund instead of fulfilling.
                    p.setStatus(Payment.PAID);
                    providers.byCode(p.getProvider()).ifPresent(pr -> {
                        if (pr.refund(p.getProviderReference(), p.getAmount())) p.setStatus(Payment.REFUNDED);
                    });
                }
            }
            case FAILED, CANCELLED -> {
                String status = outcome == PaymentProvider.Outcome.FAILED ? Payment.FAILED : Payment.CANCELLED;
                p.setStatus(status);
                o.setPaymentStatus(status);
                if (OrderStateMachine.PENDING_PAYMENT.equals(o.getStatus())) {
                    lifecycle.releaseReservations(o);   // stock released per policy; cart stays untouched
                    lifecycle.record(o, OrderStateMachine.CANCELLED, "Payment " + status.toLowerCase(), "payment-provider");
                }
            }
            default -> { }
        }
        p.setUpdatedAt(clock.instant());
    }

    /** Signed provider callback. Signature = hex HMAC-SHA256 over the raw body. */
    @Transactional
    public void handleWebhook(String providerCode, String rawBody, String signature) {
        String secret = props.getPayment().getWebhookSecret();
        if (secret == null || secret.isBlank() || signature == null
                || !Hashing.constantTimeEquals(Hashing.hmacSha256Hex(secret, rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)), signature.trim().toLowerCase())) {
            throw new UnauthenticatedException("Invalid webhook signature.");
        }
        PaymentProvider provider = providers.byCode(providerCode).orElseThrow(() -> new ResourceNotFoundException("Provider"));
        String eventId = field(rawBody, "eventId");
        String reference = field(rawBody, "providerReference");
        String status = field(rawBody, "status");
        if (eventId == null || reference == null || status == null) throw new ValidationFailedException("body", "Malformed webhook payload.");
        int inserted = em.createNativeQuery("insert ignore into payment_webhook_events (provider_code, event_id, received_at) values (?1, ?2, ?3)")
                .setParameter(1, provider.code()).setParameter(2, eventId).setParameter(3, java.sql.Timestamp.from(clock.instant())).executeUpdate();
        if (inserted == 0) return;   // duplicate delivery: nothing to do
        Payment p = payments.findByProviderReference(reference).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        PaymentProvider.Outcome outcome;
        try {
            outcome = PaymentProvider.Outcome.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationFailedException("status", "Unknown payment status.");
        }
        apply(p, outcome);
    }

    /** Releases reservations for online orders whose payment never completed (idempotent). */
    @Transactional
    public void expire(Long orderId) {
        CustomerOrder o = orders.findById(orderId).orElse(null);
        if (o == null || !OrderStateMachine.PENDING_PAYMENT.equals(o.getStatus())) return;
        payments.findByOrderIdOrderByIdAsc(orderId).stream().filter(x -> Payment.INITIATED.equals(x.getStatus())).findFirst()
                .ifPresent(x -> apply(x, PaymentProvider.Outcome.CANCELLED));
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]{1,100})\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
