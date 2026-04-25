package io.github.ZeroFishKo.ZGuard.impl;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;

import java.util.Collection;
import java.util.Map;

/**
 * 确保值不为空/null 的校验器。
 * <p>
 * 此校验器处理多种类型：
 * </p>
 * <ul>
 *   <li>{@link String} – 检查 trim 后字符串是否非空。</li>
 *   <li>{@link Collection} – 检查集合是否非空。</li>
 *   <li>{@link Map} – 检查映射是否非空。</li>
 *   <li>数组 – 检查数组长度是否大于零。</li>
 *   <li>其他对象 – 视为非空（校验通过）。</li>
 * </ul>
 *
 * @see ValidatorHandler
 */
public class RequiredValidator implements ValidatorHandler {

    /**
     * 根据类型校验值是否非空。
     *
     * @param value 要检查的值
     * @return 如果值非空返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null) return false;
        if (value instanceof String) return !((String) value).trim().isEmpty();
        if (value instanceof Collection) return !((Collection<?>) value).isEmpty();
        if (value instanceof Map) return !((Map<?, ?>) value).isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) > 0;
        return true;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return "字段不能为空"
     */
    @Override
    public String getErrorMessage() {
        return "字段不能为空";
    }

    /**
     * 此校验器的优先级（1 = 最高）。应尽早运行，以便在缺少数据时快速失败。
     *
     * @return 优先级值
     */
    @Override
    public int getPriority() {
        return 1;
    }

    /**
     * 此校验器的唯一键。
     *
     * @return "required"
     */
    @Override
    public String getValidatorKey() {
        return "required";
    }
}