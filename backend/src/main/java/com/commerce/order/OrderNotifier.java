package com.commerce.order;

import com.commerce.config.AppProperties;
import com.commerce.notification.OutboxService;
import com.commerce.order.OrderRepositories.OrderItemRepository;
import com.commerce.shipment.ShipmentRepository;
import com.commerce.user.AppUser;
import com.commerce.user.AppUserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Queues customer emails for the order milestones (placed, shipped, delivered, cancelled).
 * Messages go through the email outbox inside the caller's transaction, so a rolled-back order never sends mail and a
 * slow or failing SMTP server never blocks or fails an order. The text is plain and built from server-side data only.
 */
@Component
public class OrderNotifier {
    public record Content(String subject, String body) {}

    private final OutboxService outbox;
    private final AppUserRepository users;
    private final OrderItemRepository items;
    private final ShipmentRepository shipments;
    private final AppProperties props;

    public OrderNotifier(OutboxService outbox, AppUserRepository users, OrderItemRepository items, ShipmentRepository shipments, AppProperties props) {
        this.outbox = outbox;
        this.users = users;
        this.items = items;
        this.shipments = shipments;
        this.props = props;
    }

    /** Called for every status change; only customer-relevant milestones produce an email. */
    public void statusChanged(CustomerOrder o, String status) {
        if (!isMilestone(status)) return;
        AppUser u = users.findById(o.getUserId()).orElse(null);
        if (u == null || u.getEmail() == null || u.getEmail().isBlank()) return;

        List<String> itemLines = new ArrayList<>();
        for (OrderItem i : items.findByOrderIdOrderByIdAsc(o.getId())) itemLines.add(i.getTitle() + " x " + i.getQuantity());
        List<String> tracking = new ArrayList<>();
        if (OrderStateMachine.SHIPPED.equals(status)) {
            for (var s : shipments.findByOrderIdOrderByIdAsc(o.getId())) tracking.add(s.getCarrier() + " - " + s.getTrackingNumber());
        }
        String base = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().replaceAll("/+$", "");
        Content c = compose(status, o.getOrderNumber(), u.getFullName(), o.getTotal(), o.getCurrency(), itemLines, tracking,
                base + "/account/orders/" + o.getId());
        if (c != null) outbox.enqueue("ORDER_" + status, u.getEmail(), c.subject(), c.body(), "ORDER", o.getId());
    }

    static boolean isMilestone(String status) {
        return OrderStateMachine.PLACED.equals(status) || OrderStateMachine.SHIPPED.equals(status)
                || OrderStateMachine.DELIVERED.equals(status) || OrderStateMachine.CANCELLED.equals(status);
    }

    /** Pure message builder (no I/O), so it can be unit-tested. Returns null for statuses that send no email. */
    static Content compose(String status, String orderNumber, String name, BigDecimal total, String currency,
                           List<String> itemLines, List<String> trackingLines, String orderUrl) {
        String headline;
        String lead;
        switch (status) {
            case OrderStateMachine.PLACED -> {
                headline = "Order " + orderNumber + " confirmed";
                lead = "Thank you for your order. We have received it and will start preparing it shortly.";
            }
            case OrderStateMachine.SHIPPED -> {
                headline = "Order " + orderNumber + " has shipped";
                lead = "Your order is on its way.";
            }
            case OrderStateMachine.DELIVERED -> {
                headline = "Order " + orderNumber + " delivered";
                lead = "Your order has been delivered. We hope you love it.";
            }
            case OrderStateMachine.CANCELLED -> {
                headline = "Order " + orderNumber + " cancelled";
                lead = "Your order has been cancelled. If you paid online, any refund is returned to your original payment method.";
            }
            default -> {
                return null;
            }
        }
        StringBuilder b = new StringBuilder();
        b.append("Hello ").append(name == null || name.isBlank() ? "there" : name.trim()).append(",\n\n").append(lead).append("\n\n");
        b.append("Order: ").append(orderNumber).append('\n');
        if (total != null) b.append("Total: ").append(total.toPlainString()).append(' ').append(currency == null ? "" : currency).append('\n');
        if (itemLines != null && !itemLines.isEmpty()) {
            b.append("\nItems:\n");
            for (String l : itemLines) b.append("  - ").append(l).append('\n');
        }
        if (trackingLines != null && !trackingLines.isEmpty()) {
            b.append("\nTracking:\n");
            for (String l : trackingLines) b.append("  - ").append(l).append('\n');
        }
        b.append("\nView your order: ").append(orderUrl).append("\n\nJewel Store");
        return new Content(headline, b.toString());
    }
}
