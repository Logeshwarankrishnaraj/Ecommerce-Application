package com.commerce.order;

import com.commerce.order.OrderDtos.*;
import com.commerce.order.OrderRepositories.*;
import com.commerce.shipment.ShipmentRepository;
import com.commerce.shipment.ShipmentView;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds order views from the stored snapshots (never from live product/address data). */
@Component
public class OrderAssembler {
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final ShipmentRepository shipments;

    public OrderAssembler(OrderItemRepository items, OrderStatusHistoryRepository history, ShipmentRepository shipments) {
        this.items = items;
        this.history = history;
        this.shipments = shipments;
    }

    public OrderAddress address(CustomerOrder o) {
        return new OrderAddress(o.getShipRecipient(), o.getShipPhone(), o.getShipLine1(), o.getShipLine2(), o.getShipLocality(), o.getShipCity(),
                o.getShipState(), o.getShipPostalCode(), o.getShipCountry());
    }

    public List<ItemView> itemViews(Long orderId) {
        return items.findByOrderIdOrderByIdAsc(orderId).stream().map(i -> new ItemView(i.getId(), i.getProductId(), i.getSku(), i.getTitle(),
                i.getUnitPrice(), i.getQuantity(), i.getLineTotal(), i.getImageKey() == null ? null : "/media/" + i.getImageKey() + "_t.jpg")).toList();
    }

    public OrderDetail detail(CustomerOrder o) {
        List<ShipmentView> ships = shipments.findByOrderIdOrderByIdAsc(o.getId()).stream().map(ShipmentView::of).toList();
        List<HistoryView> hist = history.findByOrderIdOrderByChangedAtAscIdAsc(o.getId()).stream().map(h -> new HistoryView(h.getStatus(), h.getChangedAt())).toList();
        return new OrderDetail(o.getId(), o.getOrderNumber(), o.getStatus(), o.getPaymentMethod(), o.getPaymentStatus(), o.getSubtotal(), o.getShippingAmount(),
                o.getTaxAmount(), o.getDiscountAmount(), o.getTotal(), o.getCurrency(), o.getCreatedAt(), address(o), itemViews(o.getId()), ships, hist,
                OrderStateMachine.customerCanCancel(o.getStatus()));
    }
}
