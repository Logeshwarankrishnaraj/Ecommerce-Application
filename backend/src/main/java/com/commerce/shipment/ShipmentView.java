package com.commerce.shipment;

import java.time.Instant;

public record ShipmentView(Long shipmentId, String carrier, String trackingNumber, String status, Instant shippedAt, Instant deliveredAt,
                           String trackingNote) {
    public static ShipmentView of(Shipment s) {
        return new ShipmentView(s.getId(), s.getCarrier(), s.getTrackingNumber(), s.getStatus(), s.getShippedAt(), s.getDeliveredAt(), s.getTrackingNote());
    }
}
