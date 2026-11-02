package com.commerce.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class InventoryRepositories {
    private InventoryRepositories() {}

    public interface InventoryRepository extends JpaRepository<Inventory, Long> {
        /** Row lock used by every stock-changing operation so concurrent checkouts serialize (SHOP-11). */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select i from Inventory i where i.productId = :id")
        Optional<Inventory> findForUpdate(@Param("id") Long productId);

        @Query("select count(i) from Inventory i, Product p where p.id = i.productId and p.status <> 'ARCHIVED' and (i.onHand - i.reserved) <= i.lowStockThreshold")
        long countLowStock();

        @Query("select i from Inventory i, Product p where p.id = i.productId and p.status <> 'ARCHIVED' "
                + "and (:q is null or lower(p.title) like :q escape '!' or lower(p.sku) like :q escape '!') "
                + "and (:lowOnly = false or (i.onHand - i.reserved) <= i.lowStockThreshold) order by p.title")
        Page<Inventory> search(@Param("q") String q, @Param("lowOnly") boolean lowOnly, Pageable pageable);

        List<Inventory> findByProductIdIn(Collection<Long> ids);
    }

    public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
        Page<InventoryMovement> findByProductIdOrderByIdDesc(Long productId, Pageable pageable);
    }
}
