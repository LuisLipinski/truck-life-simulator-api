package com.luislipinski.trucklife.identity.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordLengthValidatorTest {

    private final PasswordLengthValidator validator = new PasswordLengthValidator();

    @Test
    void countsUnicodeCodePointsAndAllowsSpaces() {
        assertThat(validator.isValid(" ".repeat(12), null)).isTrue();
        assertThat(validator.isValid("🚚".repeat(12), null)).isTrue();
        assertThat(validator.isValid("🚚".repeat(11), null)).isFalse();
        assertThat(validator.isValid("a".repeat(129), null)).isFalse();
    }
}
