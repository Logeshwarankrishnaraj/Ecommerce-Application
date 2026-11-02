package com.commerce.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<Payment> findByProviderReference(String providerReference);
}
