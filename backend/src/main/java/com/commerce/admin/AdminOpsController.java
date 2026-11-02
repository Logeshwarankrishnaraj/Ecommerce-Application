package com.commerce.admin;

import com.commerce.admin.AdminOpsService.*;
import com.commerce.common.api.PageResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {
    private final AdminOpsService service;

    public AdminOpsController(AdminOpsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/summary")
    public Dashboard dashboard() { return service.dashboard(); }

    @GetMapping("/errors")
    public PageResponse<ErrorRow> errors(@RequestParam(required = false) String status, @RequestParam(required = false) String severity,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.errors(status, severity, page, size);
    }

    @GetMapping("/errors/{id}")
    public ErrorRow error(@PathVariable Long id) { return service.error(id); }

    @PostMapping("/errors/{id}/acknowledge")
    public ErrorRow acknowledge(@PathVariable Long id) { return service.acknowledge(id); }

    @GetMapping("/audit-logs")
    public PageResponse<AuditRow> audit(@RequestParam(required = false) String action, @RequestParam(required = false) String actor,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.auditLogs(action, actor, page, size);
    }

    /** Super admins only; see SecurityConfig. POST (not DELETE) because the body lists the ids. */
    @PostMapping("/audit-logs/delete")
    public AuditDeleteResult deleteAudit(@RequestBody AuditDeleteRequest body) { return service.deleteAuditLogs(body); }

    @GetMapping("/users")
    public PageResponse<UserRow> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                       @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.users(q, role, status, page, size);
    }

    @PostMapping("/users/{id}/status")
    public UserRow status(@PathVariable Long id, @RequestBody StatusRequest body) { return service.setStatus(id, body); }

    @PostMapping("/users/{id}/roles")
    public UserRow role(@PathVariable Long id, @RequestBody RoleRequest body) { return service.setRole(id, body); }
}
