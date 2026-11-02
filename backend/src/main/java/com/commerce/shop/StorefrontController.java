package com.commerce.shop;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontController {
    private final StorefrontService service;

    public StorefrontController(StorefrontService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public StorefrontService.Config config() {
        return service.config();
    }

    @GetMapping("/home")
    public java.util.Map<String, Object> home() {
        return java.util.Map.of("blocks", service.page("home").blocks());
    }

    @GetMapping("/pages/{slug}")
    public StorefrontService.Page page(@PathVariable String slug) {
        return service.page(slug);
    }
}
