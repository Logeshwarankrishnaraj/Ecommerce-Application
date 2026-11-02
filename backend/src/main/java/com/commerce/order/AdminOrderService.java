package com.commerce.order;

import com.commerce.audit.AuditService;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.BusinessRuleException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.util.Like;
import com.commerce.imports.Csv;
import com.commerce.order.OrderDtos.*;
import com.commerce.order.OrderRepositories.*;
import com.commerce.payment.Payment;
import com.commerce.payment.PaymentRepository;
import com.commerce.security.CurrentUser;
import com.commerce.shipment.Shipment;
import com.commerce.shipment.ShipmentRepository;
import com.commerce.shipment.ShipmentView;
import com.commerce.shop.ShopRepositories.ShopProfileRepository;
import com.commerce.user.AppUser;
import com.commerce.user.AppUserRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin fulfilment: explicit status transitions, shipments and packing notes. Every change is audited. */
@Service
public class AdminOrderService {
    public record ShipmentRequest(String carrier, String trackingNumber, String status, String trackingNote) {}

    private static final Set<String> SHIPMENT_STATUSES = Set.of("PENDING", "SHIPPED", "DELIVERED");
    /** Hard cap so one export request cannot load the whole orders table. */
    static final int MAX_EXPORT_ROWS = 5000;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;
    private final AppUserRepository users;
    private final ShopProfileRepository shop;
    private final OrderLifecycle lifecycle;
    private final OrderAssembler assembler;
    private final AuditService audit;
    private final Clock clock;

    public AdminOrderService(OrderRepository orders, OrderItemRepository orderItems, ShipmentRepository shipments, PaymentRepository payments, AppUserRepository users,
                             ShopProfileRepository shop, OrderLifecycle lifecycle, OrderAssembler assembler, AuditService audit, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.shipments = shipments;
        this.payments = payments;
        this.users = users;
        this.shop = shop;
        this.lifecycle = lifecycle;
        this.assembler = assembler;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderRow> list(String status, String q, int page, int size) {
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (st != null && !OrderStateMachine.isKnown(st)) throw new ValidationFailedException("status", "Unknown order status.");
        String like = q == null || q.isBlank() ? null : Like.contains(q.trim().toLowerCase());
        var p = orders.adminSearch(st, like, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        return PageResponse.of(p, o -> new AdminOrderRow(o.getId(), o.getOrderNumber(), users.findById(o.getUserId()).map(AppUser::getFullName).orElse("-"),
                o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(), o.getTotal(), o.getCreatedAt()));
    }

    /**
     * CSV of the orders matching the same filters as the list screen (newest first, at most {@link #MAX_EXPORT_ROWS} rows).
     * Opens in Excel (UTF-8 BOM); every cell goes through {@link Csv#cell} so text starting with = + - @ cannot run as a formula.
     * Not read-only on purpose: the export itself is written to the audit log.
     */
    @Transactional
    public String exportCsv(String status, String q) {
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (st != null && !OrderStateMachine.isKnown(st)) throw new ValidationFailedException("status", "Unknown order status.");
        String like = q == null || q.isBlank() ? null : Like.contains(q.trim().toLowerCase());
        StringBuilder out = new StringBuilder("\uFEFF");
        out.append(Csv.line(List.of("Order number", "Placed at (UTC)", "Customer", "Email", "Status", "Payment method", "Payment status", "Units",
                "Subtotal", "Shipping", "Tax", "Discount", "Total", "Currency", "Ship to city", "Ship to state", "Postal code")));
        Map<Long, AppUser> customers = new HashMap<>();
        int rows = 0;
        int page = 0;
        boolean more = true;
        while (more && rows < MAX_EXPORT_ROWS) {
            var batch = orders.adminSearch(st, like, PageRequest.of(page++, 500));
            for (CustomerOrder o : batch.getContent()) {
                if (rows >= MAX_EXPORT_ROWS) break;
                AppUser u = customers.computeIfAbsent(o.getUserId(), id -> users.findById(id).orElse(null));
                out.append(Csv.line(List.of(
                        nz(o.getOrderNumber()), String.valueOf(o.getCreatedAt()), u == null ? "" : nz(u.getFullName()), u == null ? "" : nz(u.getEmail()),
                        nz(o.getStatus()), nz(o.getPaymentMethod()), nz(o.getPaymentStatus()), String.valueOf(orderItems.totalQuantity(o.getId())),
                        money(o.getSubtotal()), money(o.getShippingAmount()), money(o.getTaxAmount()), money(o.getDiscountAmount()), money(o.getTotal()),
                        nz(o.getCurrency()), nz(o.getShipCity()), nz(o.getShipState()), nz(o.getShipPostalCode()))));
                rows++;
            }
            more = batch.hasNext();
        }
        audit.record("ORDER_EXPORT", "ORDER", "CSV", rows + " orders exported" + (st == null ? "" : " (status " + st + ")"));
        return out.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String money(java.math.BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    @Transactional(readOnly = true)
    public AdminOrderDetail detail(Long id) {
        return view(load(id));
    }

    @Transactional
    public AdminOrderDetail changeStatus(Long id, StatusRequest req) {
        CustomerOrder o = load(id);
        String to = req == null || req.status() == null ? "" : req.status().trim().toUpperCase();
        if (!OrderStateMachine.isKnown(to)) throw new ValidationFailedException("status", "Unknown order status.");
        String from = o.getStatus();
        if (!OrderStateMachine.adminCanTransition(from, to)) {
            throw new BusinessRuleException("An order cannot move from " + from + " to " + to + ".");
        }
        String note = clean(req.note(), 300);
        switch (to) {
            case OrderStateMachine.CANCELLED -> lifecycle.cancel(o, CurrentUser.label(), note == null ? "Cancelled by admin" : note);
            case OrderStateMachine.SHIPPED -> {
                if (shipments.findByOrderIdOrderByIdAsc(id).isEmpty()) throw new BusinessRuleException("Add a shipment with a tracking number before marking the order as shipped.");
                lifecycle.record(o, to, note, CurrentUser.label());
            }
            case OrderStateMachine.DELIVERED -> {
                lifecycle.record(o, to, note, CurrentUser.label());
                markDeliveredEffects(o);
            }
            default -> lifecycle.record(o, to, note, CurrentUser.label());
        }
        audit.record("ORDER_STATUS", "ORDER", o.getOrderNumber(), from + " -> " + to);
        return view(o);
    }

    @Transactional
    public AdminOrderDetail setPackingNotes(Long id, String notes) {
        CustomerOrder o = load(id);
        o.setPackingNotes(clean(notes, 1000));
        o.setUpdatedAt(clock.instant());
        audit.record("ORDER_PACKING_NOTES", "ORDER", o.getOrderNumber(), "updated");
        return view(o);
    }

    @Transactional
    public AdminOrderDetail addShipment(Long orderId, ShipmentRequest req) {
        CustomerOrder o = load(orderId);
        if (!Set.of(OrderStateMachine.CONFIRMED, OrderStateMachine.PACKING, OrderStateMachine.SHIPPED).contains(o.getStatus())) {
            throw new BusinessRuleException("Shipments can only be added once an order is confirmed.");
        }
        Shipment s = new Shipment();
        s.setOrderId(orderId);
        s.setCarrier(requireText(req == null ? null : req.carrier(), "carrier", 80));
        s.setTrackingNumber(requireText(req.trackingNumber(), "trackingNumber", 80));
        s.setTrackingNote(clean(req.trackingNote(), 500));
        s.setShipFromSnapshot(shop.findById(1L).map(sp -> sp.shipFromLine()).orElse(null));   // immutable record of the dispatch address
        s.setStatus("PENDING");
        s.setCreatedAt(clock.instant());
        s.setUpdatedAt(clock.instant());
        s = shipments.save(s);
        String wanted = normaliseShipmentStatus(req.status());
        if (!"PENDING".equals(wanted)) applyShipmentStatus(o, s, wanted);
        audit.record("SHIPMENT_ADD", "ORDER", o.getOrderNumber(), "carrier " + s.getCarrier());
        return view(o);
    }

    @Transactional
    public AdminOrderDetail updateShipment(Long shipmentId, ShipmentRequest req) {
        Shipment s = shipments.findById(shipmentId).orElseThrow(() -> new ResourceNotFoundException("Shipment"));
        CustomerOrder o = load(s.getOrderId());
        if (OrderStateMachine.CANCELLED.equals(o.getStatus())) throw new BusinessRuleException("This order is cancelled.");
        if (req.carrier() != null) s.setCarrier(requireText(req.carrier(), "carrier", 80));
        if (req.trackingNumber() != null) s.setTrackingNumber(requireText(req.trackingNumber(), "trackingNumber", 80));
        if (req.trackingNote() != null) s.setTrackingNote(clean(req.trackingNote(), 500));
        s.setUpdatedAt(clock.instant());
        if (req.status() != null) {
            String wanted = normaliseShipmentStatus(req.status());
            if (!wanted.equals(s.getStatus())) applyShipmentStatus(o, s, wanted);
        }
        audit.record("SHIPMENT_UPDATE", "ORDER", o.getOrderNumber(), "shipment " + shipmentId);
        return view(o);
    }

    /** Shipment progress auto-advances the order: first SHIPPED moves it to SHIPPED; all DELIVERED moves it to DELIVERED. */
    private void applyShipmentStatus(CustomerOrder o, Shipment s, String wanted) {
        String current = s.getStatus();
        int from = rank(current), to = rank(wanted);
        if (to < from) throw new BusinessRuleException("A shipment cannot move backwards from " + current + " to " + wanted + ".");
        if (to > 0 && !Set.of(OrderStateMachine.CONFIRMED, OrderStateMachine.PACKING, OrderStateMachine.SHIPPED, OrderStateMachine.DELIVERED).contains(o.getStatus())) {
            throw new BusinessRuleException("The order is not ready to ship.");
        }
        var now = clock.instant();
        s.setStatus(wanted);
        s.setUpdatedAt(now);
        if (to >= 1 && s.getShippedAt() == null) s.setShippedAt(now);
        if (to == 2) s.setDeliveredAt(now);
        String actor = CurrentUser.label();
        if (to >= 1) {
            if (OrderStateMachine.CONFIRMED.equals(o.getStatus())) lifecycle.record(o, OrderStateMachine.PACKING, "Packed", actor);
            if (OrderStateMachine.PACKING.equals(o.getStatus())) lifecycle.record(o, OrderStateMachine.SHIPPED, "Shipment dispatched", actor);
        }
        if (to == 2 && OrderStateMachine.SHIPPED.equals(o.getStatus())) {
            shipments.flush();
            boolean all = shipments.findByOrderIdOrderByIdAsc(o.getId()).stream().allMatch(x -> "DELIVERED".equals(x.getStatus()));
            if (all) {
                lifecycle.record(o, OrderStateMachine.DELIVERED, "All shipments delivered", actor);
                markDeliveredEffects(o);
            }
        }
    }

    private void markDeliveredEffects(CustomerOrder o) {
        if ("COD".equals(o.getPaymentMethod()) && !Payment.COLLECTED.equals(o.getPaymentStatus())) {
            o.setPaymentStatus(Payment.COLLECTED);
            payments.findByOrderIdOrderByIdAsc(o.getId()).stream().reduce((a, b) -> b).ifPresent(p -> {
                p.setStatus(Payment.COLLECTED);
                p.setUpdatedAt(clock.instant());
            });
        }
    }

    private static int rank(String s) {
        return switch (s) { case "PENDING" -> 0; case "SHIPPED" -> 1; case "DELIVERED" -> 2; default -> throw new ValidationFailedException("status", "Unknown shipment status."); };
    }

    private static String normaliseShipmentStatus(String s) {
        String v = s == null || s.isBlank() ? "PENDING" : s.trim().toUpperCase();
        if (!SHIPMENT_STATUSES.contains(v)) throw new ValidationFailedException("status", "Shipment status must be PENDING, SHIPPED or DELIVERED.");
        return v;
    }

    private CustomerOrder load(Long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order"));
    }

    private AdminOrderDetail view(CustomerOrder o) {
        OrderDetail d = assembler.detail(o);
        AppUser u = users.findById(o.getUserId()).orElse(null);
        CustomerInfo c = u == null ? new CustomerInfo("-", "-", null) : new CustomerInfo(u.getFullName(), u.getEmail(), o.getShipPhone());
        return new AdminOrderDetail(d.orderId(), d.orderNumber(), d.status(), d.paymentMethod(), d.paymentStatus(), d.subtotal(), d.shippingAmount(),
                d.taxAmount(), d.discountAmount(), d.total(), d.currency(), d.createdAt(), d.shippingAddress(), d.items(), d.shipments(), d.statusHistory(),
                c, OrderStateMachine.adminTransitions(o.getStatus()), o.getPackingNotes());
    }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.indexOf('<') >= 0 || t.indexOf('>') >= 0) throw new ValidationFailedException("text", "Angle brackets are not allowed.");
        if (t.length() > max) throw new ValidationFailedException("text", "Must be at most " + max + " characters.");
        return t;
    }

    private static String requireText(String s, String field, int max) {
        String t = clean(s, max);
        if (t == null) throw new ValidationFailedException(field, "This field is required.");
        return t;
    }
}
