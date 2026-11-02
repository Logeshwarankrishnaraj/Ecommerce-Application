package com.commerce.wishlist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/wishlist")
public class WishlistController {
    public record AddRequest(@NotNull Long productId) {}

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @GetMapping
    public WishlistService.View get() {
        return service.get();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public WishlistService.View add(@Valid @RequestBody AddRequest body) {
        return service.add(body.productId());
    }

    @DeleteMapping("/items/{productId}")
    public WishlistService.View remove(@PathVariable Long productId) {
        return service.remove(productId);
    }
}
