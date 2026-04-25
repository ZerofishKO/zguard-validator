package io.github.ZeroFishKo.ZGuard.impl;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * 检查值的长度/大小的校验器。
 * <p>
 * 此校验器适用于：
 * </p>
 * <ul>
 *   <li>{@link String} – 检查字符串长度。</li>
 *   <li>{@link Collection} – 检查集合大小。</li>
 *   <li>{@link Map} – 检查映射大小。</li>
 *   <li>数组 – 检查数组长度。</li>
 * </ul>
 * <p>
 * 允许的长度范围 [min, max] 可通过 {@link #setExtParams(Map)} 配置。
 * </p>
 *
 * @see ValidatorHandler
 */
public class LengthValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(LengthValidator.class);
    private int min = 0;
    private int max = Integer.MAX_VALUE;

    /**
     * 校验值的长度/大小。
     *
     * @param value 值（String、Collection、Map 或数组）
     * @return 如果长度在 [min, max] 内返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null) return false;
        int length = 0;
        if (value instanceof String) length = ((String) value).length();
        else if (value instanceof Collection) length = ((Collection<?>) value).size();
        else if (value instanceof Map) length = ((Map<?, ?>) value).size();
        else if (value.getClass().isArray()) length = java.lang.reflect.Array.getLength(value);
        return length >= min && length <= max;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 指示允许的长度范围的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "长度必须在 " + min + " 和 " + max + " 之间";
    }

    @Override
    public Object[] getMessageArguments() {
        return new Object[]{min, max};
    }

    /**
     * 此校验器的优先级（默认 5，可覆盖）。
     *
     * @return 优先级值
     */
    @Override
    public int getPriority() {
        return 5;
    }

    /**
     * 此校验器的唯一键。
     *
     * @return "length"
     */
    @Override
    public String getValidatorKey() {
        return "length";
    }

    /**
     * 动态更新最小和最大长度值。
     * <p>
     * 期望一个可能包含以下键的映射：
     * </p>
     * <ul>
     *   <li>{@code "min"} – 新的最小长度（int）。</li>
     *   <li>{@code "max"} – 新的最大长度（int）。</li>
     * </ul>
     *
     * @param extParams 包含新范围的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        if (extParams == null) return;

        // 更新 min
        if (extParams.containsKey("min")) {
            Object val = extParams.get("min");
            if (val instanceof Integer) {
                this.min = (Integer) val;
            } else if (val instanceof String) {
                try {
                    this.min = Integer.parseInt((String) val);
                } catch (NumberFormatException e) {
                    log.error("min 值无效：{}", val, e);
                }
            } else if (val instanceof Number) {
                this.min = ((Number) val).intValue();
            }
        }

        // 更新 max
        if (extParams.containsKey("max")) {
            Object val = extParams.get("max");
            if (val instanceof Integer) {
                this.max = (Integer) val;
            } else if (val instanceof String) {
                try {
                    this.max = Integer.parseInt((String) val);
                } catch (NumberFormatException e) {
                    log.error("max 值无效：{}", val, e);
                }
            } else if (val instanceof Number) {
                this.max = ((Number) val).intValue();
            }
        }

        // 确保 min <= max
        if (this.min > this.max) {
            log.warn("min ({}) > max ({})，交换值", this.min, this.max);
            int temp = this.min;
            this.min = this.max;
            this.max = temp;
        }
    }
}