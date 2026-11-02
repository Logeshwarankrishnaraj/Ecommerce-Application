package com.commerce.payment;

import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.order.OrderRepositories.OrderRepository;
import com.commerce.security.CurrentUser;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** LOCAL/TEST ONLY: simulates the gateway side of the mock provider. Does not exist in production profiles. */
@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/dev/mock-payments")
public class DevMockPaymentController {
    public record OutcomeRequest(String outcome) {}

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final Optional<MockPaymentProvider> mock;

    public DevMockPaymentController(PaymentRepository payments, OrderRepository orders, Optional<MockPaymentProvider> mock) {
        this.payments = payments;
        this.orders = orders;
        this.mock = mock;
    }

    @PostMapping("/{paymentId}/outcome")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void outcome(@PathVariable Long paymentId, @RequestBody OutcomeRequest body) {
        Long uid = CurrentUser.requireId();
        Payment p = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        orders.findByIdAndUserId(p.getOrderId(), uid).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        PaymentProvider.Outcome o = switch (body.outcome() == null ? "" : body.outcome()) {
            case "SUCCESS" -> PaymentProvider.Outcome.PAID;
            case "FAILURE" -> PaymentProvider.Outcome.FAILED;
            case "CANCEL" -> PaymentProvider.Outcome.CANCELLED;
            default -> throw new com.commerce.common.exception.ValidationFailedException("outcome", "Use SUCCESS, FAILURE or CANCEL.");
        };
        mock.orElseThrow(() -> new ResourceNotFoundException("Mock provider")).simulate(p.getProviderReference(), o);
    }
}
