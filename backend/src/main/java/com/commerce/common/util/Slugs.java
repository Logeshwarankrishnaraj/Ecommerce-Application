package com.commerce.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private Slugs() {}

    public static String slugify(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String s = NON_ALNUM.matcher(n.toLowerCase(Locale.ROOT)).replaceAll("-");
        s = s.replaceAll("^-+|-+$", "");
        return s.length() > 100 ? s.substring(0, 100).replaceAll("-+$", "") : s;
    }

    public static boolean isValid(String slug) {
        return slug != null && slug.length() <= 120 && VALID.matcher(slug).matches();
    }
}
