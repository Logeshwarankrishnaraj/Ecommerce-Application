package com.commerce.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class OrderRepositories {
    private OrderRepositories() {}

    public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
        Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

        Optional<CustomerOrder> findByUserIdAndIdempotencyKey(Long userId, String key);

        boolean existsByOrderNumber(String orderNumber);

        Page<CustomerOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

        @Query("select o from CustomerOrder o where (:status is null or o.status = :status) and (:q is null or lower(o.orderNumber) like :q escape '!' "
                + "or exists (select 1 from com.commerce.user.AppUser u where u.id = o.userId and (lower(u.fullName) like :q escape '!' or lower(u.email) like :q escape '!'))) "
                + "order by o.createdAt desc, o.id desc")
        Page<CustomerOrder> adminSearch(@Param("status") String status, @Param("q") String q, Pageable pageable);

        @Query("select o.status, count(o) from CustomerOrder o group by o.status")
        List<Object[]> countByStatus();

        @Query("select coalesce(sum(o.total), 0) from CustomerOrder o where o.status <> 'CANCELLED' and o.status <> 'PENDING_PAYMENT'")
        java.math.BigDecimal grossValue();

        @Query("select o from CustomerOrder o where o.status = 'PENDING_PAYMENT' and o.createdAt < :before")
        List<CustomerOrder> findStalePending(@Param("before") Instant before);
    }

    public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
        List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

        long countByOrderId(Long orderId);

        @Query("select coalesce(sum(i.quantity), 0) from OrderItem i where i.orderId = :orderId")
        long totalQuantity(@Param("orderId") Long orderId);

        /** Best sellers = units sold in completed (non-cancelled, paid-or-COD) orders. */
        @Query("select i.productId from OrderItem i, CustomerOrder o where o.id = i.orderId and o.status not in ('CANCELLED','PENDING_PAYMENT') "
                + "and i.productId is not null group by i.productId order by sum(i.quantity) desc")
        List<Long> bestSellerIds(Pageable pageable);
    }

    public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
        List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
    }
}
