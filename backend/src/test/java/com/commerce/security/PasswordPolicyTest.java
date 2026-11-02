package com.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void acceptsStrong() {
        assertThat(PasswordPolicy.violation("Admin@12345!")).isNull();
    }

    @Test
    void rejectsEachMissingClass() {
        assertThat(PasswordPolicy.violation("Short1!")).contains("10 characters");
        assertThat(PasswordPolicy.violation("alllowercase1!")).contains("uppercase");
        assertThat(PasswordPolicy.violation("ALLUPPERCASE1!")).contains("lowercase");
        assertThat(PasswordPolicy.violation("NoDigitsHere!!")).contains("digit");
        assertThat(PasswordPolicy.violation("NoSymbolsHere12")).contains("symbol");
    }
}
