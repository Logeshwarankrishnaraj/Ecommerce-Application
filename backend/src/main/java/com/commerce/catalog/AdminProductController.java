package com.commerce.catalog;

import com.commerce.catalog.CatalogDtos.*;
import com.commerce.common.api.PageResponse;
import com.commerce.common.exception.ValidationFailedException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {
    private final AdminProductService service;

    public AdminProductController(AdminProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminProductRow> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(q, status, categoryId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProduct create(@RequestBody ProductUpsert body) {
        return service.create(body);
    }

    @GetMapping("/{id}")
    public AdminProduct get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public AdminProduct update(@PathVariable Long id, @RequestBody ProductUpsert body) {
        return service.update(id, body);
    }

    @PostMapping("/{id}/publish")
    public AdminProduct publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    public AdminProduct unpublish(@PathVariable Long id) {
        return service.unpublish(id);
    }

    @PostMapping("/{id}/archive")
    public AdminProduct archive(@PathVariable Long id) {
        return service.archive(id);
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    public AdminProduct addImage(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "altText", required = false) String altText) {
        try {
            return service.addImage(id, file.getBytes(), altText);
        } catch (IOException e) {
            throw new ValidationFailedException("file", "The upload could not be read.");
        }
    }

    @PatchMapping("/{id}/images/order")
    public AdminProduct reorder(@PathVariable Long id, @RequestBody ImageOrderRequest body) {
        return service.reorderImages(id, body.imageIds());
    }

    @PatchMapping("/{id}/images/{imageId}")
    public AdminProduct patchImage(@PathVariable Long id, @PathVariable Long imageId, @RequestBody ImagePatchRequest body) {
        return service.patchImage(id, imageId, body);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public AdminProduct deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return service.deleteImage(id, imageId);
    }
}
