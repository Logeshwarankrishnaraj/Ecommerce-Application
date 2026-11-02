package com.commerce.lookup;

import com.commerce.audit.AuditService;
import com.commerce.common.exception.*;
import com.commerce.common.web.PublicCache;
import com.commerce.lookup.LookupRepositories.*;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-driven filter/lookup values (SRS 6.1 list_of_values, 7.2). */
@Service
public class LookupService {
    private static final Pattern CODE = Pattern.compile("^[a-z0-9_]{1,50}$");

    public record PublicValue(String valueCode, String displayLabel) {}
    public record GroupRow(String groupCode, String label, long valueCount) {}
    public record ValueRow(Long lovId, String valueCode, String displayLabel, boolean active, int sortOrder) {}
    public record ValueCreate(String valueCode, String displayLabel, Integer sortOrder) {}
    public record ValuePatch(String displayLabel, Boolean active, Integer sortOrder) {}
    public record AttributeRow(String code, String label, String lovGroupCode, boolean filterable) {}

    private final LovGroupRepository groups;
    private final ListOfValueRepository values;
    private final AttributeDefinitionRepository attributes;
    private final AuditService audit;
    private final PublicCache cache;

    public LookupService(LovGroupRepository groups, ListOfValueRepository values, AttributeDefinitionRepository attributes,
                         AuditService audit, PublicCache cache) {
        this.groups = groups;
        this.values = values;
        this.attributes = attributes;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<PublicValue> publicValues(String groupCode) {
        LovGroup g = groups.findById(groupCode).filter(LovGroup::isPublicGroup).orElseThrow(() -> new ResourceNotFoundException("Lookup group"));
        return cache.get("lov:" + g.getGroupCode(), 30, () -> values.findByGroupCodeAndActiveTrueOrderBySortOrderAscDisplayLabelAsc(g.getGroupCode())
                .stream().map(v -> new PublicValue(v.getValueCode(), v.getDisplayLabel())).toList());
    }

    @Transactional(readOnly = true)
    public List<GroupRow> adminGroups() {
        return groups.findAll().stream().map(g -> new GroupRow(g.getGroupCode(), g.getLabel(), values.countByGroupCode(g.getGroupCode()))).toList();
    }

    @Transactional(readOnly = true)
    public List<ValueRow> adminValues(String groupCode) {
        groups.findById(groupCode).orElseThrow(() -> new ResourceNotFoundException("Lookup group"));
        return values.findByGroupCodeOrderBySortOrderAscDisplayLabelAsc(groupCode).stream().map(LookupService::row).toList();
    }

    @Transactional
    public ValueRow create(String groupCode, ValueCreate req) {
        groups.findById(groupCode).orElseThrow(() -> new ResourceNotFoundException("Lookup group"));
        String code = req.valueCode() == null ? "" : req.valueCode().trim().toLowerCase();
        if (!CODE.matcher(code).matches()) throw new ValidationFailedException("valueCode", "Use lowercase letters, digits and underscores (max 50).");
        if (req.displayLabel() == null || req.displayLabel().isBlank() || req.displayLabel().length() > 100) {
            throw new ValidationFailedException("displayLabel", "Display label is required (max 100 characters).");
        }
        if (values.findByGroupCodeAndValueCode(groupCode, code).isPresent()) throw new DuplicateResourceException("valueCode", "That value code already exists in this group.");
        ListOfValue v = new ListOfValue();
        v.setGroupCode(groupCode);
        v.setValueCode(code);
        v.setDisplayLabel(req.displayLabel().trim());
        v.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        v = values.save(v);
        audit.record("LOOKUP_CREATE", "LOV", v.getId(), groupCode + ":" + code);
        cache.clear();
        return row(v);
    }

    @Transactional
    public ValueRow patch(Long id, ValuePatch req) {
        ListOfValue v = values.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lookup value"));
        if (req.displayLabel() != null) {
            if (req.displayLabel().isBlank() || req.displayLabel().length() > 100) throw new ValidationFailedException("displayLabel", "Display label is required (max 100 characters).");
            v.setDisplayLabel(req.displayLabel().trim());
        }
        if (req.active() != null) v.setActive(req.active());
        if (req.sortOrder() != null) v.setSortOrder(req.sortOrder());
        audit.record("LOOKUP_UPDATE", "LOV", id, v.getGroupCode() + ":" + v.getValueCode() + " active=" + v.isActive());
        cache.clear();
        return row(v);
    }

    @Transactional
    public void delete(Long id) {
        ListOfValue v = values.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lookup value"));
        if (values.countUsage(v.getGroupCode(), v.getValueCode()) > 0) {
            throw new ConflictException("This value is used by products. Deactivate it instead of deleting.");
        }
        values.delete(v);
        audit.record("LOOKUP_DELETE", "LOV", id, v.getGroupCode() + ":" + v.getValueCode());
        cache.clear();
    }

    @Transactional(readOnly = true)
    public List<AttributeRow> attributeDefinitions() {
        return attributes.findAllByOrderBySortOrderAsc().stream().filter(AttributeDefinition::isActive)
                .map(a -> new AttributeRow(a.getCode(), a.getLabel(), a.getLovGroupCode(), a.isFilterable())).toList();
    }

    private static ValueRow row(ListOfValue v) {
        return new ValueRow(v.getId(), v.getValueCode(), v.getDisplayLabel(), v.isActive(), v.getSortOrder());
    }
}
