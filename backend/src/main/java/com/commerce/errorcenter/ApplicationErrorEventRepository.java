package com.commerce.errorcenter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationErrorEventRepository extends JpaRepository<ApplicationErrorEvent, Long> {
    @Query("select e from ApplicationErrorEvent e where e.signature = :sig and e.lastOccurredAt >= :since order by e.lastOccurredAt desc")
    List<ApplicationErrorEvent> findRecentBySignature(@Param("sig") String sig, @Param("since") Instant since, Pageable pageable);

    @Query("select e from ApplicationErrorEvent e where (:status is null or e.remediationState = :status) and (:severity is null or e.severity = :severity)")
    Page<ApplicationErrorEvent> search(@Param("status") String status, @Param("severity") String severity, Pageable pageable);

    long countByRemediationState(String state);

    Optional<ApplicationErrorEvent> findFirstByCorrelationId(String correlationId);

    List<ApplicationErrorEvent> findTop5ByOrderByLastOccurredAtDesc();
}
