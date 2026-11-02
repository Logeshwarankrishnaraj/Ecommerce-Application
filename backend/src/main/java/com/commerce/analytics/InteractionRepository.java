package com.commerce.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InteractionRepository extends JpaRepository<ProductInteractionEvent, Long> {
    boolean existsByDedupeKeyAndCreatedAtAfter(String dedupeKey, Instant after);

    @Query("select e.eventType, count(e) from ProductInteractionEvent e where e.createdAt >= :from and e.createdAt < :to group by e.eventType")
    List<Object[]> totals(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select e.productId, e.eventType, count(e) from ProductInteractionEvent e where e.createdAt >= :from and e.createdAt < :to group by e.productId, e.eventType")
    List<Object[]> perProduct(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select cast(e.createdAt as date), e.eventType, count(e) from ProductInteractionEvent e where e.createdAt >= :from and e.createdAt < :to group by cast(e.createdAt as date), e.eventType")
    List<Object[]> daily(@Param("from") Instant from, @Param("to") Instant to);

    @Modifying
    @Query("delete from ProductInteractionEvent e where e.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
