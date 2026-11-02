package com.commerce.cart;

import com.commerce.order.CheckoutQuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/cart")
public class CartController {
    public record AddRequest(@NotNull Long productId, @NotNull Integer quantity) {}
    public record QuantityRequest(@NotNull Integer quantity) {}

    private final CartService service;
    private final CheckoutQuoteService quotes;

    public CartController(CartService service, CheckoutQuoteService quotes) {
        this.service = service;
        this.quotes = quotes;
    }

    @GetMapping
    public CartService.View get() {
        return service.get();
    }

    @PostMapping("/items")
    public CartService.View add(@Valid @RequestBody AddRequest body) {
        return service.add(body.productId(), body.quantity());
    }

    @PatchMapping("/items/{cartItemId}")
    public CartService.View update(@PathVariable Long cartItemId, @Valid @RequestBody QuantityRequest body) {
        return service.updateQuantity(cartItemId, body.quantity());
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartService.View remove(@PathVariable Long cartItemId) {
        return service.remove(cartItemId);
    }

    @PostMapping("/checkout-preview")
    public CheckoutQuoteService.Quote preview() {
        return quotes.quoteForCurrentUser();
    }
}
