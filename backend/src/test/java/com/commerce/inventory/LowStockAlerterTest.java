package com.commerce.inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LowStockAlerterTest {
    @Test
    void alertsWhenStockFirstReachesTheThreshold() {
        assertTrue(LowStockAlerter.crossedDown(6, 5, 5));   // lands exactly on the threshold
        assertTrue(LowStockAlerter.crossedDown(10, 0, 5));  // jumps well below it
    }

    @Test
    void doesNotRepeatWhileAlreadyLow() {
        assertFalse(LowStockAlerter.crossedDown(5, 4, 5));
        assertFalse(LowStockAlerter.crossedDown(2, 1, 5));
    }

    @Test
    void staysQuietWhenStockIsHealthyOrRising() {
        assertFalse(LowStockAlerter.crossedDown(20, 15, 5));
        assertFalse(LowStockAlerter.crossedDown(3, 12, 5));
    }
}
