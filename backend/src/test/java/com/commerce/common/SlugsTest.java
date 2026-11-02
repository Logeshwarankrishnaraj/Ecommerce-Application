package com.commerce.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.common.util.Slugs;
import org.junit.jupiter.api.Test;

class SlugsTest {
    @Test
    void slugifiesAccentsAndSymbols() {
        assertThat(Slugs.slugify("  Rosé Gold Ring (18K)! ")).isEqualTo("rose-gold-ring-18k");
    }

    @Test
    void validatesShape() {
        assertThat(Slugs.isValid("gold-ring")).isTrue();
        assertThat(Slugs.isValid("Gold Ring")).isFalse();
        assertThat(Slugs.isValid("-bad")).isFalse();
        assertThat(Slugs.isValid(null)).isFalse();
    }
}
