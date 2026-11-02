package com.commerce.order;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderNotifierTest {
    private static OrderNotifier.Content build(String status, String name, List<String> tracking) {
        return OrderNotifier.compose(status, "JW20261101-ABC234", name, new BigDecimal("1299.50"), "INR",
                List.of("Gold ring x 1", "Chain x 2"), tracking, "https://shop.example/account/orders/7");
    }

    @Test
    void onlyCustomerMilestonesSendMail() {
        assertTrue(OrderNotifier.isMilestone("PLACED"));
        assertTrue(OrderNotifier.isMilestone("SHIPPED"));
        assertTrue(OrderNotifier.isMilestone("DELIVERED"));
        assertTrue(OrderNotifier.isMilestone("CANCELLED"));
        assertFalse(OrderNotifier.isMilestone("PENDING_PAYMENT"));
        assertFalse(OrderNotifier.isMilestone("CONFIRMED"));
        assertFalse(OrderNotifier.isMilestone("PACKING"));
    }

    @Test
    void placedMailHasOrderNumberTotalItemsAndLink() {
        var c = build("PLACED", "Asha", List.of());
        assertEquals("Order JW20261101-ABC234 confirmed", c.subject());
        assertTrue(c.body().startsWith("Hello Asha,"));
        assertTrue(c.body().contains("Total: 1299.50 INR"));
        assertTrue(c.body().contains("Gold ring x 1"));
        assertTrue(c.body().contains("https://shop.example/account/orders/7"));
        assertFalse(c.body().contains("Tracking:"));
    }

    @Test
    void shippedMailListsTracking() {
        var c = build("SHIPPED", "Asha", List.of("Blue Dart - BD123"));
        assertEquals("Order JW20261101-ABC234 has shipped", c.subject());
        assertTrue(c.body().contains("Tracking:\n  - Blue Dart - BD123"));
    }

    @Test
    void blankNameFallsBackToGenericGreeting() {
        assertTrue(build("DELIVERED", " ", List.of()).body().startsWith("Hello there,"));
        assertTrue(build("CANCELLED", null, List.of()).body().startsWith("Hello there,"));
    }

    @Test
    void otherStatusesProduceNoMessage() {
        assertNull(build("PACKING", "Asha", List.of()));
    }
}
