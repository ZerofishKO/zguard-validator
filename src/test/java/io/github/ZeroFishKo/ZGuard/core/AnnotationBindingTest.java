package io.github.ZeroFishKo.ZGuard.core;

import io.github.ZeroFishKo.ZGuard.annotation.ValidateWith;
import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBindingTest {

    static class TestRequest {
        @ValidateWith({"required", "paymentCode"})
        String code;
    }

    private ZGuardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ZGuardValidator(new ValidatorConfig() {}, "test");
        validator.registerValidator(new RequiredValidator());
        validator.registerValidator(new PaymentCodeValidator());
        validator.bindAnnotations(TestRequest.class);
    }

    @Test
    void testAnnotationBinding() {
        var data = new java.util.HashMap<String, Object>();
        data.put("code", "123456");
        assertTrue(validator.validate(data).isSuccess());

        data.put("code", "12345");
        assertFalse(validator.validate(data).isSuccess());
    }
}