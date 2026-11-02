package com.commerce.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    @Query("select o from EmailOutbox o where o.state = 'PENDING' and o.nextAttemptAt <= :now order by o.nextAttemptAt")
    List<EmailOutbox> findDue(@Param("now") Instant now, Pageable pageable);

    @Query("select count(o) from EmailOutbox o where o.state = 'PENDING'")
    long countPending();
}
