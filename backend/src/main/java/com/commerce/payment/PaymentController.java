package com.commerce.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/{paymentId}/verify")
    public PaymentService.Verification verify(@PathVariable Long paymentId) {
        return service.verify(paymentId);
    }

    /** Provider-signed callback; CSRF-exempt, authenticated by HMAC signature. */
    @PostMapping("/webhooks/{providerCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@PathVariable String providerCode, @RequestBody String rawBody,
                        @RequestHeader(value = "X-Signature", required = false) String signature) {
        service.handleWebhook(providerCode, rawBody, signature);
    }
}
