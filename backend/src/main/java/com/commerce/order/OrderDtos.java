package com.commerce.order;

import com.commerce.shipment.ShipmentView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderAddress(String recipientName, String phone, String line1, String line2, String locality, String city, String state,
                               String postalCode, String country) {}

    public record ItemView(Long orderItemId, Long productId, String sku, String title, BigDecimal unitPrice, int quantity,
                           BigDecimal lineTotal, String imageUrl) {}

    public record HistoryView(String status, Instant at) {}

    public record OrderDetail(Long orderId, String orderNumber, String status, String paymentMethod, String paymentStatus, BigDecimal subtotal,
                              BigDecimal shippingAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal total, String currency,
                              Instant createdAt, OrderAddress shippingAddress, List<ItemView> items, List<ShipmentView> shipments,
                              List<HistoryView> statusHistory, boolean cancellable) {}

    public record PaymentInfo(Long paymentId, String status, String provider, boolean mock, boolean requiresAction) {}

    public record PlacedOrder(Long orderId, String orderNumber, String status, String paymentMethod, String paymentStatus, BigDecimal subtotal,
                              BigDecimal shippingAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal total, String currency,
                              Instant createdAt, OrderAddress shippingAddress, List<ItemView> items, List<ShipmentView> shipments,
                              List<HistoryView> statusHistory, boolean cancellable, PaymentInfo payment) {
        public static PlacedOrder of(OrderDetail d, PaymentInfo p) {
            return new PlacedOrder(d.orderId(), d.orderNumber(), d.status(), d.paymentMethod(), d.paymentStatus(), d.subtotal(), d.shippingAmount(),
                    d.taxAmount(), d.discountAmount(), d.total(), d.currency(), d.createdAt(), d.shippingAddress(), d.items(), d.shipments(),
                    d.statusHistory(), d.cancellable(), p);
        }
    }

    public record OrderSummary(Long orderId, String orderNumber, String status, String paymentMethod, String paymentStatus, BigDecimal total,
                               String currency, Instant createdAt, long itemCount) {}

    public record PlaceOrderRequest(Long addressId, String paymentMethod, BigDecimal expectedTotal, boolean acceptedTerms) {}

    public record CustomerInfo(String name, String email, String phone) {}

    public record AdminOrderRow(Long orderId, String orderNumber, String customerName, String status, String paymentMethod, String paymentStatus,
                                BigDecimal total, Instant createdAt) {}

    public record AdminOrderDetail(Long orderId, String orderNumber, String status, String paymentMethod, String paymentStatus, BigDecimal subtotal,
                                   BigDecimal shippingAmount, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal total, String currency,
                                   Instant createdAt, OrderAddress shippingAddress, List<ItemView> items, List<ShipmentView> shipments,
                                   List<HistoryView> statusHistory, CustomerInfo customer, List<String> allowedTransitions, String packingNotes) {}

    public record StatusRequest(String status, String note) {}
    public record PackingNotesRequest(String packingNotes) {}
}
