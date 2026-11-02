package com.commerce.order;

import com.commerce.common.exception.BusinessRuleException;
import com.commerce.inventory.InventoryService;
import com.commerce.order.OrderRepositories.*;
import com.commerce.payment.Payment;
import com.commerce.payment.PaymentProvider;
import com.commerce.payment.PaymentProviders;
import com.commerce.payment.PaymentRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Status changes, stock and payment side effects that must always travel together (single transaction). */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OrderLifecycle {
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final PaymentRepository payments;
    private final PaymentProviders providers;
    private final InventoryService inventory;
    private final OrderNotifier notifier;
    private final Clock clock;

    public OrderLifecycle(OrderItemRepository items, OrderStatusHistoryRepository history, PaymentRepository payments,
                          PaymentProviders providers, InventoryService inventory, OrderNotifier notifier, Clock clock) {
        this.items = items;
        this.history = history;
        this.payments = payments;
        this.providers = providers;
        this.inventory = inventory;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void record(CustomerOrder o, String status, String note, String actor) {
        o.setStatus(status);
        o.setUpdatedAt(clock.instant());
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(o.getId());
        h.setStatus(status);
        h.setNote(note);
        h.setActor(actor);
        h.setChangedAt(clock.instant());
        history.save(h);
        notifier.statusChanged(o, status);   // queued in the same transaction; only placed/shipped/delivered/cancelled send mail
    }

    private List<OrderItem> sortedItems(Long orderId) {
        return items.findByOrderIdOrderByIdAsc(orderId).stream()
                .filter(i -> i.getProductId() != null)
                .sorted(Comparator.comparing(OrderItem::getProductId)).toList();   // consistent lock order
    }

    public void commitReservations(CustomerOrder o) {
        for (OrderItem i : sortedItems(o.getId())) inventory.commitReservation(i.getProductId(), i.getQuantity(), o.getOrderNumber());
    }

    public void releaseReservations(CustomerOrder o) {
        for (OrderItem i : sortedItems(o.getId())) inventory.releaseReservation(i.getProductId(), i.getQuantity(), o.getOrderNumber());
    }

    /** Cancels an order from any cancellable state and reverses stock and payment accordingly. */
    public void cancel(CustomerOrder o, String actor, String note) {
        if (!OrderStateMachine.systemCanTransition(o.getStatus(), OrderStateMachine.CANCELLED)) {
            throw new BusinessRuleException("An order that is " + o.getStatus().toLowerCase().replace('_', ' ') + " can no longer be cancelled.");
        }
        Payment pay = payments.findByOrderIdOrderByIdAsc(o.getId()).stream().reduce((a, b) -> b).orElse(null);
        if (OrderStateMachine.PENDING_PAYMENT.equals(o.getStatus())) {
            releaseReservations(o);
            if (pay != null && Payment.INITIATED.equals(pay.getStatus())) pay.setStatus(Payment.CANCELLED);
            o.setPaymentStatus(Payment.CANCELLED);
        } else {
            for (OrderItem i : sortedItems(o.getId())) inventory.restock(i.getProductId(), i.getQuantity(), o.getOrderNumber());
            if ("COD".equals(o.getPaymentMethod())) {
                if (pay != null) pay.setStatus(Payment.CANCELLED);
                o.setPaymentStatus(Payment.CANCELLED);
            } else if (pay != null && Payment.PAID.equals(pay.getStatus())) {
                PaymentProvider provider = providers.byCode(pay.getProvider()).orElseThrow(() -> new BusinessRuleException("The payment provider is unavailable, so the refund cannot be issued."));
                if (!provider.refund(pay.getProviderReference(), pay.getAmount())) throw new BusinessRuleException("The refund could not be processed. The order was not cancelled.");
                pay.setStatus(Payment.REFUNDED);
                o.setPaymentStatus(Payment.REFUNDED);
            }
        }
        if (pay != null) pay.setUpdatedAt(clock.instant());
        record(o, OrderStateMachine.CANCELLED, note, actor);
    }
}
