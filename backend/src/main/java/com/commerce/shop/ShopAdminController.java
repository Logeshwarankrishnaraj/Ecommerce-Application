package com.commerce.shop;

import com.commerce.shop.ShopAdminService.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class ShopAdminController {
    private final ShopAdminService service;
    private final SettingsService settings;

    public ShopAdminController(ShopAdminService service, SettingsService settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping("/shop-profile")
    public ProfileDto profile() { return service.profile(); }

    @PutMapping("/shop-profile")
    public ProfileDto saveProfile(@RequestBody ProfileDto body) { return service.saveProfile(body); }

    @GetMapping("/site-settings")
    public List<SettingsService.SettingRow> settings() { return settings.adminList(); }

    public record SettingsPatch(Map<String, String> values) {}

    @PatchMapping("/site-settings")
    public List<SettingsService.SettingRow> patchSettings(@RequestBody SettingsPatch body) { return settings.update(body.values()); }

    @GetMapping("/site-links")
    public List<LinkDto> links() { return service.links(); }

    @PutMapping("/site-links")
    public List<LinkDto> saveLinks(@RequestBody List<LinkDto> body) { return service.saveLinks(body); }

    @GetMapping("/content-pages")
    public List<PageDto> pages() { return service.pages(); }

    @PostMapping("/content-pages")
    @ResponseStatus(HttpStatus.CREATED)
    public PageDto create(@RequestBody PageCreate body) { return service.createPage(body); }

    @PatchMapping("/content-pages/{id}")
    public PageDto update(@PathVariable Long id, @RequestBody PagePatch body) { return service.updatePage(id, body); }
}
