package com.commerce.common.util;

import java.util.Locale;

public final class Like {
    private Like() {}

    /** Lower-cases and escapes LIKE wildcards (escape char '!') then wraps in %...%; null/blank -> null. */
    public static String contains(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + t + "%";
    }
}
