package io.github.ZeroFishKoZGuard.core;

import io.github.ZeroFishKoZGuard.impl.RequiredValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequiredValidatorTest {

    private final RequiredValidator validator = new RequiredValidator();

    @Test
    void testValidateWithNull() {
        assertFalse(validator.validate(null));
    }

    @Test
    void testValidateWithEmptyString() {
        assertFalse(validator.validate(""));
        assertFalse(validator.validate("   "));
    }

    @Test
    void testValidateWithNonEmptyString() {
        assertTrue(validator.validate("hello"));
    }

    @Test
    void testValidateWithEmptyCollection() {
        assertFalse(validator.validate(java.util.Collections.emptyList()));
    }

    @Test
    void testValidateWithNonEmptyCollection() {
        assertTrue(validator.validate(java.util.List.of("a")));
    }
}