package com.commerce.order;

import com.commerce.common.api.PageResponse;
import com.commerce.order.OrderDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/checkout/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacedOrder place(@RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody PlaceOrderRequest req) {
        return service.place(key, req);
    }

    @GetMapping("/account/orders")
    public PageResponse<OrderSummary> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.mine(page, size);
    }

    @GetMapping("/account/orders/{orderId}")
    public OrderDetail detail(@PathVariable Long orderId) {
        return service.mineDetail(orderId);
    }

    @PostMapping("/account/orders/{orderId}/cancel")
    public OrderDetail cancel(@PathVariable Long orderId) {
        return service.cancelMine(orderId);
    }
}
