package com.commerce.shop;

import com.commerce.audit.AuditService;
import com.commerce.common.exception.ValidationFailedException;
import com.commerce.common.web.PublicCache;
import com.commerce.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Typed access to validated site_settings (no arbitrary HTML/JS values). */
@Service
public class SettingsService {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Set<String> HIGH_RISK = Set.of("payments.cod.enabled", "payments.online.enabled", "currency.code", "tax.ratePercent");

    public record SettingRow(String key, String value, String group, String type, String description, List<String> options) {}

    private final SiteSettingRepository repo;
    private final AuditService audit;
    private final PublicCache cache;
    private final Clock clock;

    public SettingsService(SiteSettingRepository repo, AuditService audit, PublicCache cache, Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        return cache.get("settings", 30, () -> {
            Map<String, String> m = new HashMap<>();
            for (SiteSetting s : repo.findAll()) m.put(s.getKey(), s.getValue());
            return m;
        });
    }

    public String get(String key, String def) {
        return all().getOrDefault(key, def);
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal def) {
        try {
            return new BigDecimal(get(key, def.toPlainString()).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    /** Width and height ratio parts from media.aspectRatio ("1:1", "4:5", "3:4"). */
    public int[] aspectRatio() {
        String[] p = get("media.aspectRatio", "1:1").split(":");
        try {
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (RuntimeException e) {
            return new int[] {1, 1};
        }
    }

    @Transactional(readOnly = true)
    public List<SettingRow> adminList() {
        return repo.findAll().stream().sorted(Comparator.comparing(SiteSetting::getGroup).thenComparing(SiteSetting::getKey))
                .map(s -> new SettingRow(s.getKey(), s.getValue(), s.getGroup(), s.getType(), s.getDescription(),
                        s.getOptions() == null ? null : Arrays.asList(s.getOptions().split(",")))).toList();
    }

    @Transactional
    public List<SettingRow> update(Map<String, String> values) {
        if (values == null || values.isEmpty()) throw new ValidationFailedException("values", "No settings supplied.");
        for (Map.Entry<String, String> e : values.entrySet()) {
            SiteSetting s = repo.findById(e.getKey()).orElseThrow(() -> new ValidationFailedException(e.getKey(), "Unknown setting."));
            String v = e.getValue() == null ? "" : e.getValue().trim();
            validate(s, v);
            if (!v.equals(s.getValue())) {
                if (HIGH_RISK.contains(s.getKey())) audit.record("SETTING_HIGH_RISK_CHANGE", "SETTING", s.getKey(), s.getKey() + ": " + s.getValue() + " -> " + v);
                else audit.record("SETTING_CHANGE", "SETTING", s.getKey(), s.getKey() + " updated");
            }
            s.setValue(v);
            s.setUpdatedBy(CurrentUser.label());
            s.setUpdatedAt(clock.instant());
        }
        cache.clear();
        return adminList();
    }

    static void validate(SiteSetting s, String v) {
        String key = s.getKey();
        switch (s.getType()) {
            case "COLOR" -> { if (!HEX.matcher(v).matches()) throw new ValidationFailedException(key, "Use a hex color like #FAF6EE."); }
            case "ENUM" -> { if (s.getOptions() == null || !Arrays.asList(s.getOptions().split(",")).contains(v)) throw new ValidationFailedException(key, "Choose one of: " + s.getOptions()); }
            case "BOOLEAN" -> { if (!v.equals("true") && !v.equals("false")) throw new ValidationFailedException(key, "Must be true or false."); }
            case "INTEGER" -> {
                try { if (Integer.parseInt(v) < 0 || Integer.parseInt(v) > 1000) throw new NumberFormatException(); }
                catch (NumberFormatException e) { throw new ValidationFailedException(key, "Enter a whole number between 0 and 1000."); }
            }
            case "DECIMAL" -> {
                try { if (new BigDecimal(v).signum() < 0 || new BigDecimal(v).compareTo(new BigDecimal("1000000")) > 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { throw new ValidationFailedException(key, "Enter a non-negative number."); }
            }
            default -> {
                if (v.length() > 2000) throw new ValidationFailedException(key, "Too long.");
                if (v.contains("<") || v.contains(">")) throw new ValidationFailedException(key, "HTML is not allowed; use plain text.");
            }
        }
        if (key.equals("currency.code") && !CURRENCY.matcher(v).matches()) throw new ValidationFailedException(key, "Use a 3-letter ISO currency code.");
        if (key.equals("tax.ratePercent") && new BigDecimal(v).compareTo(new BigDecimal("100")) > 0) throw new ValidationFailedException(key, "Tax rate cannot exceed 100.");
    }
}
