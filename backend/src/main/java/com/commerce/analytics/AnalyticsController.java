package com.commerce.analytics;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/** Admin-only (enforced by URL rule /api/v1/admin/** and never exposed through customer APIs). */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public AnalyticsService.Response products(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "20") int limit) {
        return service.report(from, to, limit);
    }
}
