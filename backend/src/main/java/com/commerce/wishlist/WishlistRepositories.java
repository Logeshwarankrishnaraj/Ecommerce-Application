package com.commerce.wishlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public final class WishlistRepositories {
    private WishlistRepositories() {}

    public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
        Optional<Wishlist> findByUserId(Long userId);
    }

    public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
        List<WishlistItem> findByWishlistIdOrderByAddedAtDesc(Long wishlistId);

        Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
    }
}
