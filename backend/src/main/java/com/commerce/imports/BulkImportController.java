package com.commerce.imports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/imports")
public class BulkImportController {
    private final BulkImportService service;

    public BulkImportController(BulkImportService service) {
        this.service = service;
    }

    @GetMapping("/products/template")
    public ResponseEntity<byte[]> template() {
        return csv(service.template(), "products-template.csv");
    }

    @PostMapping(value = "/products/preview", consumes = "multipart/form-data")
    public BulkImportService.JobView preview(@RequestParam("file") MultipartFile file, @RequestParam(value = "images", required = false) MultipartFile images,
                                             @RequestParam(defaultValue = "CREATE") String mode, @RequestParam(defaultValue = "false") boolean allowPartial) throws IOException {
        return service.preview(file.getOriginalFilename(), file.getBytes(), images == null ? null : images.getBytes(), mode, allowPartial);
    }

    @PostMapping("/products/{jobId}/commit")
    public BulkImportService.JobView commit(@PathVariable Long jobId) {
        return service.commit(jobId);
    }

    @GetMapping("/{jobId}")
    public BulkImportService.JobView get(@PathVariable Long jobId) {
        return service.get(jobId);
    }

    @GetMapping("/{jobId}/errors.csv")
    public ResponseEntity<byte[]> errors(@PathVariable Long jobId) {
        return csv(service.errorsCsv(jobId), "import-" + jobId + "-errors.csv");
    }

    private static ResponseEntity<byte[]> csv(String body, String name) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"").body(body.getBytes(StandardCharsets.UTF_8));
    }
}
