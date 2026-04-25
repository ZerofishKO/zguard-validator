package io.github.ZeroFishKoZGuard.annotation;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;

import java.lang.annotation.*;

/**
 * 用于将一个或多个校验器绑定到字段的注解。
 * <p>
 * 该注解用于类的字段上，以指定当类被 {@link ZGuardValidator#bindAnnotations(Class[])}
 * 处理时应应用哪些校验器。其值是一个校验器键数组，每个键对应一个已注册校验器的
 * {@link ValidatorHandler#getValidatorKey()}。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * public class PaymentRequest {
 *     @ValidateWith({"required", "web3Wallet", "riskAddr"})
 *     private String walletAddr;
 *
 *     @ValidateWith({"required", "paymentCode"})
 *     private String paymentCode;
 *
 *     @ValidateWith({"required", "range"})
 *     private Double transferAmount;
 *
 *     @ValidateWith({"required", "web3ChainId"})
 *     private String chainId;
 * }
 * }</pre>
 *
 * @see ZGuardValidator#bindAnnotations(Class[])
 * @see ValidatorHandler#getValidatorKey()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidateWith {
    /**
     * 将绑定到注解字段的校验器键数组。
     * <p>
     * 每个键必须匹配已注册到 {@code ZGuardValidator} 实例的校验器的
     * {@link ValidatorHandler#getValidatorKey()}。
     * </p>
     *
     * @return 校验器键
     */
    String[] value();
}