package com.commerce.catalog;

import com.commerce.catalog.CatalogDtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryNode> tree() {
        return service.publicTree();
    }

    @GetMapping("/admin/categories")
    public List<AdminCategory> adminList() {
        return service.adminList();
    }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategory create(@RequestBody CategoryUpsert body) {
        return service.create(body);
    }

    @PatchMapping("/admin/categories/{id}")
    public AdminCategory update(@PathVariable Long id, @RequestBody CategoryUpsert body) {
        return service.update(id, body);
    }

    @DeleteMapping("/admin/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
