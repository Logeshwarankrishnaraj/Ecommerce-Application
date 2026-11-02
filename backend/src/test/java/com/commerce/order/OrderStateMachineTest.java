package com.commerce.order;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderStateMachineTest {
    @Test
    void adminCannotMarkUnpaidOnlineOrderAsPlaced() {
        assertFalse(OrderStateMachine.adminCanTransition("PENDING_PAYMENT", "PLACED"));
        assertTrue(OrderStateMachine.systemCanTransition("PENDING_PAYMENT", "PLACED"));
    }

    @Test
    void happyPathIsAllowed() {
        assertTrue(OrderStateMachine.adminCanTransition("PLACED", "CONFIRMED"));
        assertTrue(OrderStateMachine.adminCanTransition("CONFIRMED", "PACKING"));
        assertTrue(OrderStateMachine.adminCanTransition("PACKING", "SHIPPED"));
        assertTrue(OrderStateMachine.adminCanTransition("SHIPPED", "DELIVERED"));
    }

    @Test
    void skippingOrGoingBackwardsIsRejected() {
        assertFalse(OrderStateMachine.adminCanTransition("PLACED", "SHIPPED"));
        assertFalse(OrderStateMachine.adminCanTransition("DELIVERED", "SHIPPED"));
        assertFalse(OrderStateMachine.adminCanTransition("SHIPPED", "CANCELLED"));
    }

    @Test
    void terminalStatesHaveNoTransitions() {
        assertTrue(OrderStateMachine.adminTransitions("DELIVERED").isEmpty());
        assertTrue(OrderStateMachine.adminTransitions("CANCELLED").isEmpty());
        assertTrue(OrderStateMachine.adminTransitions("NOPE").isEmpty());
    }

    @Test
    void customersCancelOnlyBeforePacking() {
        assertTrue(OrderStateMachine.customerCanCancel("PENDING_PAYMENT"));
        assertTrue(OrderStateMachine.customerCanCancel("PLACED"));
        assertTrue(OrderStateMachine.customerCanCancel("CONFIRMED"));
        assertFalse(OrderStateMachine.customerCanCancel("PACKING"));
        assertFalse(OrderStateMachine.customerCanCancel("SHIPPED"));
        assertFalse(OrderStateMachine.customerCanCancel("DELIVERED"));
    }
}
