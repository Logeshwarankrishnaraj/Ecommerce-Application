package com.commerce.audit;

import com.commerce.common.logging.CorrelationIdFilter;
import com.commerce.errorcenter.ErrorEventService;
import com.commerce.security.CurrentUser;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records security-sensitive admin actions. Joins the caller's transaction so audit and change commit together. */
@Service
public class AuditService {
    private final AdminAuditLogRepository repo;
    private final Clock clock;

    public AuditService(AdminAuditLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String targetType, Object targetId, String summary) {
        record(CurrentUser.label(), action, targetType, targetId, summary, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String actor, String action, String targetType, Object targetId, String summary, String outcome) {
        AdminAuditLog a = new AdminAuditLog();
        a.setActor(actor);
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(String.valueOf(targetId));
        a.setSummary(ErrorEventService.sanitize(summary));
        a.setOutcome(outcome);
        a.setCorrelationId(CorrelationIdFilter.current());
        a.setCreatedAt(clock.instant());
        repo.save(a);
    }
}
