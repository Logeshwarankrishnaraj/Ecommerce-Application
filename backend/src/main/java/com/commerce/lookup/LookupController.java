package com.commerce.lookup;

import com.commerce.lookup.LookupService.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LookupController {
    private final LookupService service;

    public LookupController(LookupService service) {
        this.service = service;
    }

    @GetMapping("/lookup-groups/{groupCode}/values")
    public List<PublicValue> publicValues(@PathVariable String groupCode) {
        return service.publicValues(groupCode);
    }

    @GetMapping("/admin/lookups")
    public List<GroupRow> groups() {
        return service.adminGroups();
    }

    @GetMapping("/admin/lookups/{groupCode}")
    public List<ValueRow> values(@PathVariable String groupCode) {
        return service.adminValues(groupCode);
    }

    @PostMapping("/admin/lookups/{groupCode}/values")
    @ResponseStatus(HttpStatus.CREATED)
    public ValueRow create(@PathVariable String groupCode, @RequestBody ValueCreate body) {
        return service.create(groupCode, body);
    }

    @PatchMapping("/admin/lookups/values/{id}")
    public ValueRow patch(@PathVariable Long id, @RequestBody ValuePatch body) {
        return service.patch(id, body);
    }

    @DeleteMapping("/admin/lookups/values/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/admin/attribute-definitions")
    public List<AttributeRow> attributes() {
        return service.attributeDefinitions();
    }
}
