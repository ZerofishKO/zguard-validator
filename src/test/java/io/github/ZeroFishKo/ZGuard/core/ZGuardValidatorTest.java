package io.github.ZeroFishKo.ZGuard.core;

import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKo.ZGuard.util.hash.HashStrategy;
import io.github.ZeroFishKo.ZGuard.util.hash.HmacSha256Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZGuardValidatorTest {

    private ZGuardValidator validator;

    @BeforeEach
    void setUp() {
        // 正确实现 ValidatorConfig（不能用 lambda，因为不是函数式接口）
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"userId"};
            }
        };
        validator = new ZGuardValidator(config, "test_salt");
        validator.registerValidator(new RequiredValidator());
        validator.registerValidator(new PaymentCodeValidator());

        // 使用 Arrays.asList 替代 List.of（JDK 8 兼容）
        validator.bindFieldValidators("paymentCode", Arrays.asList("required", "paymentCode"));
        validator.bindFieldValidators("userId", Arrays.asList("required"));
    }

    @Test
    void testValidateSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", "123");
        data.put("paymentCode", "123456"); // PaymentCodeValidator 默认长度 6
        ValidateResult result = validator.validate(data);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidateMissingCoreField() {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentCode", "123456");
        // 缺少核心字段 userId
        ValidateResult result = validator.validate(data);
        assertFalse(result.isSuccess());
        assertEquals("userId", result.getFieldName());
        // 错误消息应为 "核心字段不能为空"
        assertEquals("核心字段不能为空", result.getErrorMsg());
    }

    @Test
    void testValidateInvalidPaymentCode() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", "123");
        data.put("paymentCode", "abc123"); // 包含字母
        ValidateResult result = validator.validate(data);
        assertFalse(result.isSuccess());
        assertEquals("paymentCode", result.getFieldName());
    }

    @Test
    void testValidateWithTamperProofHash() {
        // 定义自己的哈希策略（与 validator 内部一致即可）
        HashStrategy hashStrategy = new HmacSha256Strategy();
        String salt = "my_salt";

        // 注意：这里 ZGuardValidator 内部同样使用 HmacSha256Strategy（因为 DefaultValidatorConfig 中改了）
        // 但为了保证测试稳定，我们手动创建一个和 validator 配置相同的实例
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"userId"};
            }
            // 可选：覆盖哈希策略（默认 DefaultValidatorConfig 已经是 HmacSha256Strategy，但显式明确更好）
            @Override
            public HashStrategy getHashStrategy() {
                return new HmacSha256Strategy();
            }
        };
        ZGuardValidator validatorWithHash = new ZGuardValidator(config, salt);
        validatorWithHash.registerValidator(new RequiredValidator());
        validatorWithHash.bindFieldValidators("userId", Arrays.asList("required"));

        String userId = "user-001";
        // 直接使用 hashStrategy 计算哈希
        String hash = hashStrategy.hash(userId, salt);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("userId_hash", hash);

        ValidateResult result = validatorWithHash.validate(data);
        assertTrue(result.isSuccess());

        // 错误哈希
        data.put("userId_hash", "wrong_hash");
        result = validatorWithHash.validate(data);
        assertFalse(result.isSuccess());
        assertEquals("userId", result.getFieldName());
        assertEquals("数据被篡改", result.getErrorMsg());
    }
}