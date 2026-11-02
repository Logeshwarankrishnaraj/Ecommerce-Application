package com.commerce.order;

import java.util.List;
import java.util.Map;

/**
 * Explicit allow-list of order status transitions (SRS 7.3). PENDING_PAYMENT -> PLACED is system-only
 * (set when a provider-verified payment arrives); admins can never mark an unpaid online order as placed.
 */
public final class OrderStateMachine {
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PLACED = "PLACED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PACKING = "PACKING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, List<String>> NEXT = Map.of(
            PENDING_PAYMENT, List.of(PLACED, CANCELLED),
            PLACED, List.of(CONFIRMED, CANCELLED),
            CONFIRMED, List.of(PACKING, CANCELLED),
            PACKING, List.of(SHIPPED, CANCELLED),
            SHIPPED, List.of(DELIVERED),
            DELIVERED, List.of(),
            CANCELLED, List.of());

    /** Customers may cancel only before packing starts. */
    private static final List<String> CUSTOMER_CANCELLABLE = List.of(PENDING_PAYMENT, PLACED, CONFIRMED);

    private OrderStateMachine() {}

    public static boolean isKnown(String status) {
        return NEXT.containsKey(status);
    }

    /** Transitions an admin may perform manually. */
    public static List<String> adminTransitions(String from) {
        return NEXT.getOrDefault(from, List.of()).stream().filter(s -> !PLACED.equals(s)).toList();
    }

    public static boolean adminCanTransition(String from, String to) {
        return adminTransitions(from).contains(to);
    }

    public static boolean customerCanCancel(String from) {
        return CUSTOMER_CANCELLABLE.contains(from);
    }

    /** System transition used by payment verification. */
    public static boolean systemCanTransition(String from, String to) {
        return NEXT.getOrDefault(from, List.of()).contains(to);
    }
}
