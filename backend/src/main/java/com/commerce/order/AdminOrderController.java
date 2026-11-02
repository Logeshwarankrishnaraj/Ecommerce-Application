package com.commerce.order;

import com.commerce.common.api.PageResponse;
import com.commerce.order.OrderDtos.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrderController {
    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    public PageResponse<AdminOrderRow> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, page, size);
    }

    /** Download of the filtered order list as CSV. The literal path wins over /orders/{id}. */
    @GetMapping("/orders/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        byte[] body = service.exportCsv(status, q).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/orders/{id}")
    public AdminOrderDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/orders/{id}/status")
    public AdminOrderDetail status(@PathVariable Long id, @RequestBody StatusRequest req) {
        return service.changeStatus(id, req);
    }

    @PatchMapping("/orders/{id}/packing-notes")
    public AdminOrderDetail notes(@PathVariable Long id, @RequestBody PackingNotesRequest req) {
        return service.setPackingNotes(id, req.packingNotes());
    }

    @PostMapping("/orders/{id}/shipments")
    public AdminOrderDetail addShipment(@PathVariable Long id, @RequestBody AdminOrderService.ShipmentRequest req) {
        return service.addShipment(id, req);
    }

    @PatchMapping("/shipments/{id}")
    public AdminOrderDetail updateShipment(@PathVariable Long id, @RequestBody AdminOrderService.ShipmentRequest req) {
        return service.updateShipment(id, req);
    }
}
