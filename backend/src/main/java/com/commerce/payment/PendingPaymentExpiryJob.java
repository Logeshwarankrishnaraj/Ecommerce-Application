package com.commerce.payment;

import com.commerce.order.CustomerOrder;
import com.commerce.order.OrderRepositories.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Releases stock reserved by online orders that were never paid. Safe to run repeatedly. */
@Component
public class PendingPaymentExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(PendingPaymentExpiryJob.class);
    static final Duration HOLD = Duration.ofMinutes(30);
    private final OrderRepository orders;
    private final PaymentService payments;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PendingPaymentExpiryJob(OrderRepository orders, PaymentService payments, TransactionTemplate tx, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.payment.expiry-poll-ms:300000}")
    public void run() {
        var stale = tx.execute(s -> orders.findStalePending(clock.instant().minus(HOLD)).stream().map(CustomerOrder::getId).toList());
        if (stale == null) return;
        for (Long id : stale) {
            try {
                payments.expire(id);
            } catch (RuntimeException e) {
                log.warn("Could not expire pending order {}: {}", id, e.getClass().getSimpleName());
            }
        }
    }
}
