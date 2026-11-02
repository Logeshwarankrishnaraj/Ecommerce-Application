package com.commerce.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public final class CartRepositories {
    private CartRepositories() {}

    public interface CartRepository extends JpaRepository<Cart, Long> {
        Optional<Cart> findByUserId(Long userId);
    }

    public interface CartItemRepository extends JpaRepository<CartItem, Long> {
        List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

        Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

        /** Ownership-scoped: an item id from another customer's cart is simply not found. */
        Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

        void deleteByCartId(Long cartId);
    }
}
