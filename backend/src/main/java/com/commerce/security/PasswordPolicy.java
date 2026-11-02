package com.commerce.security;

import com.commerce.common.exception.ValidationFailedException;

/** Password policy: at least 10 characters with upper, lower, digit and symbol. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static String violation(String pw) {
        if (pw == null || pw.length() < 10) return "Password must be at least 10 characters long.";
        if (pw.length() > 100) return "Password must be at most 100 characters long.";
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (char c : pw.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isWhitespace(c)) symbol = true;
        }
        if (!upper) return "Password must contain an uppercase letter.";
        if (!lower) return "Password must contain a lowercase letter.";
        if (!digit) return "Password must contain a digit.";
        if (!symbol) return "Password must contain a symbol.";
        return null;
    }

    public static void require(String field, String pw) {
        String v = violation(pw);
        if (v != null) throw new ValidationFailedException(field, v);
    }
}
