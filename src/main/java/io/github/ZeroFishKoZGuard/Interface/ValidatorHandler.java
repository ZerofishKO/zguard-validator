package io.github.ZeroFishKoZGuard.Interface;

import io.github.ZeroFishKoZGuard.core.ValidateResult;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;

import java.util.Locale;
import java.util.Map;

/**
 * 组合校验、高并发、多域适配的通用校验器接口。
 * <p>
 * 所有自定义校验器都必须实现此接口。
 * 框架支持为一个字段组合多个校验器，并按优先级顺序执行，支持快速失败。
 * </p>
 *
 * @see ZGuardValidator
 */
public interface ValidatorHandler {

    /**
     * 核心校验逻辑。
     * <p>
     * 输入值可以是任意类型（String、Number、Collection 等），
     * 实现类应根据需要进行类型检查和转换。不依赖序列化。
     * </p>
     *
     * @param value 待校验的值（可能为 null）
     * @return 如果值通过校验返回 {@code true}，否则返回 {@code false}
     */
    boolean validate(Object value);

    /**
     * 校验失败时返回的错误消息。
     * <p>
     * 当校验失败时，此消息会包含在 {@link ValidateResult} 中。
     * 覆盖此方法可提供自定义错误消息。
     * </p>
     *
     * @return 错误消息（默认："Field validation failed"）
     */
    default String getErrorMessage() {
        return "Field validation failed";
    }

    default String getErrorMessage(Locale locale) {
        return getErrorMessage();
    }

    /**
     * 获取用于格式化的消息参数。
     *
     * @return 参数数组，若无参数则返回空数组
     */
    default Object[] getMessageArguments() {
        return null;
    }
    /**
     * 获取消息键（用于国际化）。
     *
     * @return 消息键，默认格式 "validator." + getValidatorKey()
     */

    default String getMessageKey() {
        return "validator." + getValidatorKey();
    }

    /**
     * 校验器的执行优先级。
     * <p>
     * 优先级值越小的校验器越先执行。推荐范围 1–10，1 为最高优先级。
     * 该值在预编译校验步骤时用于决定字段上多个校验器的执行顺序。
     * </p>
     *
     * @return 优先级值（默认：5）
     */
    default int getPriority() {
        return 5;
    }

    /**
     * 唯一标识该校验器类型的键。
     * <p>
     * 该键用于通过注解或编程方式将校验器绑定到字段。
     * 在所有已注册校验器中必须唯一。
     * </p>
     *
     * @return 校验器键（例如 "required"、"web3Wallet"）
     */
    String getValidatorKey();

    /**
     * 使用外部参数动态配置校验器。
     * <p>
     * 当配置发生变化（例如从 YAML 更新）时，框架会调用此方法，
     * 以便在不重启的情况下调整校验器行为。参数映射包含校验器特定的键值对。
     * </p>
     *
     * @param extParams 外部参数映射（可能为 {@code null} 或空）
     */
    default void setExtParams(Map<String, Object> extParams) {
        // No-op by default; override as needed
    }
}