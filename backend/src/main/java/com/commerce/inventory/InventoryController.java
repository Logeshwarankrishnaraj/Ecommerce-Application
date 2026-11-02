package com.commerce.inventory;

import com.commerce.common.api.PageResponse;
import com.commerce.inventory.InventoryService.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Row> list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean lowOnly,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(q, lowOnly, page, size);
    }

    @PostMapping("/adjustments")
    public Row adjust(@RequestBody AdjustmentRequest body) {
        return service.adjust(body);
    }

    @GetMapping("/{productId}/movements")
    public PageResponse<MovementRow> movements(@PathVariable Long productId, @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.history(productId, page, size);
    }
}
