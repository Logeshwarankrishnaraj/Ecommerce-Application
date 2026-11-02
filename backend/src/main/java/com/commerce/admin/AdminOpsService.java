package com.commerce.admin;

import com.commerce.analytics.AnalyticsService;
import com.commerce.audit.AdminAuditLog;
import com.commerce.audit.AdminAuditLogRepository;
import com.commerce.audit.AuditService;
import com.commerce.catalog.CatalogRepositories.ProductRepository;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.BusinessRuleException;
import com.commerce.common.exception.ResourceNotFoundException;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.util.Like;
import com.commerce.errorcenter.ApplicationErrorEvent;
import com.commerce.errorcenter.ApplicationErrorEventRepository;
import com.commerce.inventory.InventoryService;
import com.commerce.order.OrderRepositories.OrderRepository;
import com.commerce.security.CurrentUser;
import com.commerce.user.AppUser;
import com.commerce.user.AppUserRepository;
import com.commerce.user.Role;
import com.commerce.user.RoleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard summary, error center, audit log and user administration. */
@Service
public class AdminOpsService {
    public record ErrorRow(Long eventId, String correlationId, String severity, Instant occurredAt, Instant lastOccurredAt, int occurrenceCount,
                           String operation, String route, String errorType, String summary, String notificationState, String remediationState) {}
    public record Dashboard(String timezone, Map<String, Long> orderCountsByStatus, BigDecimal grossOrderValue, long productCount, long lowStockCount,
                            long openErrorCount, List<ErrorRow> recentErrors, AnalyticsService.Totals interactions, String from, String to) {}
    public record AuditDeleteRequest(List<Long> ids, boolean all) {}

    public record AuditDeleteResult(int deleted) {}

    public record AuditRow(Long auditId, String actor, String action, String targetType, String targetId, String summary, String outcome,
                           String correlationId, Instant createdAt) {}
    public record UserRow(Long userId, String fullName, String username, String email, String status, Set<String> roles, Instant createdAt) {}
    public record StatusRequest(String status) {}
    public record RoleRequest(String role, boolean grant) {}

    private static final Set<String> GRANTABLE = Set.of(Role.ADMIN, Role.CUSTOMER);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final ApplicationErrorEventRepository errors;
    private final AdminAuditLogRepository auditRepo;
    private final AnalyticsService analytics;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;
    private final Clock clock;

    public AdminOpsService(OrderRepository orders, ProductRepository products, InventoryService inventory, ApplicationErrorEventRepository errors,
                           AdminAuditLogRepository auditRepo, AnalyticsService analytics, AppUserRepository users, RoleRepository roles,
                           AuditService audit, Clock clock) {
        this.orders = orders;
        this.products = products;
        this.inventory = inventory;
        this.errors = errors;
        this.auditRepo = auditRepo;
        this.analytics = analytics;
        this.users = users;
        this.roles = roles;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PLACED", "CONFIRMED", "PACKING", "SHIPPED", "DELIVERED", "CANCELLED")) counts.put(s, 0L);
        for (Object[] r : orders.countByStatus()) counts.put((String) r[0], ((Number) r[1]).longValue());
        LocalDate today = LocalDate.now(clock);
        var report = analytics.report(today.minusDays(6), today, 1);
        return new Dashboard("UTC", counts, orders.grossValue(), products.countByStatusNot("ARCHIVED"), inventory.lowStockCount(),
                errors.countByRemediationState("OPEN"), errors.findTop5ByOrderByLastOccurredAtDesc().stream().map(AdminOpsService::row).toList(),
                report.totals(), today.minusDays(6).toString(), today.toString());
    }

    // ---- errors
    @Transactional(readOnly = true)
    public PageResponse<ErrorRow> errors(String status, String severity, int page, int size) {
        var p = errors.search(blank(status), blank(severity), PageRequest.of(Math.max(0, page), clamp(size), Sort.by(Sort.Direction.DESC, "lastOccurredAt")));
        return PageResponse.of(p, AdminOpsService::row);
    }

    @Transactional(readOnly = true)
    public ErrorRow error(Long id) {
        return row(errors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Error event")));
    }

    @Transactional
    public ErrorRow acknowledge(Long id) {
        ApplicationErrorEvent e = errors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Error event"));
        e.setRemediationState("ACKNOWLEDGED");
        audit.record("ERROR_ACK", "ERROR_EVENT", id, "acknowledged " + e.getCorrelationId());
        return row(e);
    }

    private static ErrorRow row(ApplicationErrorEvent e) {
        return new ErrorRow(e.getId(), e.getCorrelationId(), e.getSeverity(), e.getOccurredAt(), e.getLastOccurredAt(), e.getOccurrenceCount(), e.getOperation(),
                e.getRoute(), e.getErrorType(), e.getSummary(), e.getNotificationState(), e.getRemediationState());
    }

    // ---- audit
    @Transactional(readOnly = true)
    public PageResponse<AuditRow> auditLogs(String action, String actor, int page, int size) {
        var p = auditRepo.search(blank(action), Like.contains(actor), PageRequest.of(Math.max(0, page), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(p, a -> new AuditRow(a.getId(), a.getActor(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getSummary(), a.getOutcome(),
                a.getCorrelationId(), a.getCreatedAt()));
    }

    /**
     * Deletes the chosen audit entries (or all of them). Super admins only (enforced in SecurityConfig). The deletion is
     * recorded afterwards as a new entry, so the log always shows who removed records and how many.
     */
    @Transactional
    public AuditDeleteResult deleteAuditLogs(AuditDeleteRequest req) {
        int deleted;
        String target;
        if (req != null && req.all()) {
            deleted = auditRepo.deleteEverything();
            target = "ALL";
        } else {
            List<Long> ids = req == null || req.ids() == null ? List.of() : req.ids().stream().filter(java.util.Objects::nonNull).distinct().toList();
            if (ids.isEmpty()) throw new ValidationFailedException("ids", "Select at least one audit entry to delete.");
            if (ids.size() > 500) throw new ValidationFailedException("ids", "You can delete at most 500 entries at a time.");
            deleted = auditRepo.deleteByIdIn(ids);
            target = deleted + " selected";
        }
        audit.record("AUDIT_DELETE", "AUDIT_LOG", target, "deleted " + deleted + " audit " + (deleted == 1 ? "entry" : "entries"));
        return new AuditDeleteResult(deleted);
    }

    // ---- users
    @Transactional(readOnly = true)
    public PageResponse<UserRow> users(String q, String role, String status, int page, int size) {
        String st = blank(status);
        if (st != null && !Set.of(AppUser.ACTIVE, AppUser.DISABLED).contains(st)) throw new ValidationFailedException("status", "Unknown status.");
        String ro = blank(role);
        String qq = Like.contains(q);   // escaped %...% pattern, null when blank
        var p = users.search(qq, st, ro, PageRequest.of(Math.max(0, page), clamp(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(p, AdminOpsService::row);
    }

    @Transactional
    public UserRow setStatus(Long id, StatusRequest req) {
        AppUser u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User"));
        String s = req == null || req.status() == null ? "" : req.status().trim().toUpperCase(Locale.ROOT);
        if (!Set.of(AppUser.ACTIVE, AppUser.DISABLED).contains(s)) throw new ValidationFailedException("status", "Status must be ACTIVE or DISABLED.");
        if (u.getId().equals(CurrentUser.requireId())) throw new BusinessRuleException("You cannot change your own status.");
        if (u.roleCodes().contains(Role.SUPER_ADMIN) && !CurrentUser.isSuperAdmin()) throw new BusinessRuleException("Only a super admin can change another super admin.");
        u.setStatus(s);
        u.setUpdatedAt(clock.instant());
        audit.record("USER_STATUS", "USER", id, u.getUsername() + " -> " + s);   // SessionRevalidationFilter ends their sessions on the next request
        return row(u);
    }

    @Transactional
    public UserRow setRole(Long id, RoleRequest req) {
        if (!CurrentUser.isSuperAdmin()) throw new BusinessRuleException("Only a super admin can change roles.");
        if (req == null || req.role() == null || !GRANTABLE.contains(req.role())) throw new ValidationFailedException("role", "Role must be ROLE_ADMIN or ROLE_CUSTOMER.");
        AppUser u = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User"));
        if (u.roleCodes().contains(Role.SUPER_ADMIN)) throw new BusinessRuleException("Super admin roles cannot be changed here.");
        if (u.getId().equals(CurrentUser.requireId())) throw new BusinessRuleException("You cannot change your own roles.");
        Role role = roles.findByRoleCode(req.role()).orElseThrow(() -> new ResourceNotFoundException("Role"));
        if (req.grant()) u.getRoles().add(role);
        else u.getRoles().removeIf(r -> r.getRoleCode().equals(req.role()));
        u.setUpdatedAt(clock.instant());
        audit.record("USER_ROLE", "USER", id, (req.grant() ? "grant " : "revoke ") + req.role() + " for " + u.getUsername());
        return row(u);
    }

    private static UserRow row(AppUser u) {
        return new UserRow(u.getId(), u.getFullName(), u.getUsername(), u.getEmail(), u.getStatus(), u.roleCodes(), u.getCreatedAt());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int clamp(int size) {
        return Math.min(Math.max(1, size), 100);
    }
}
