package com.commerce.shipment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findByOrderIdOrderByIdAsc(Long orderId);
}
