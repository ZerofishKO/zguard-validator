package io.github.ZeroFishKoZGuard.impl;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.YmlConfigBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 检查数值是否在指定范围内的校验器。
 * <p>
 * 范围包含最小值和最大值。值可以是 {@link Number} 或可解析为 {@link BigDecimal} 的 {@link String}。
 * 最小值和最大值存储为 {@link BigDecimal} 以保持精度。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * validatorParams:
 *   range:
 *     min: 100.0
 *     max: 100000.0
 * </pre>
 *
 * @see ValidatorHandler
 * @see YmlConfigBinding
 */
@YmlConfigBinding(configPath = "validatorParams.range")
public class RangeValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(RangeValidator.class);
    private BigDecimal min = BigDecimal.ZERO;
    private BigDecimal max = new BigDecimal(Integer.MAX_VALUE);

    /**
     * 校验数值。
     *
     * @param value 数值（Number 或 String）
     * @return 如果值在 [min, max] 内返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null) return false;
        BigDecimal num;
        try {
            if (value instanceof Number) {
                // 使用 toString() 以保持精度
                num = new BigDecimal(value.toString());
            } else if (value instanceof String) {
                num = new BigDecimal((String) value);
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return num.compareTo(min) >= 0 && num.compareTo(max) <= 0;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 指示允许范围的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "值必须在 " + min + " 和 " + max + " 之间";
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
     * @return "range"
     */
    @Override
    public String getValidatorKey() {
        return "range";
    }

    /**
     * 动态更新最小值和最大值。
     * <p>
     * 期望一个可能包含以下键的映射：
     * </p>
     * <ul>
     *   <li>{@code "min"} – 新的最小值（Number 或 String）。</li>
     *   <li>{@code "max"} – 新的最大值（Number 或 String）。</li>
     * </ul>
     * <p>
     * 如果值无效（null 或无法解析），则忽略并记录警告。
     * 更新后，确保 min &lt;= max；如果不是，则交换它们。
     * </p>
     *
     * @param extParams 包含新范围的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        if (extParams == null) return;

        // 安全转换的辅助 lambda
        java.util.function.Consumer<String> updateMin = (str) -> {
            try {
                this.min = new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.error("min 值无效：{}", str, e);
            }
        };
        java.util.function.Consumer<String> updateMax = (str) -> {
            try {
                this.max = new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.error("max 值无效：{}", str, e);
            }
        };

        // 更新 min
        if (extParams.containsKey("min")) {
            Object obj = extParams.get("min");
            if (obj != null) {
                if (obj instanceof BigDecimal) {
                    this.min = (BigDecimal) obj;
                } else if (obj instanceof Number) {
                    this.min = new BigDecimal(obj.toString());
                } else if (obj instanceof String) {
                    updateMin.accept((String) obj);
                } else {
                    log.warn("min 的类型不支持：{}", obj.getClass().getName());
                }
            } else {
                log.warn("min 值为 null，忽略");
            }
        }

        // 更新 max
        if (extParams.containsKey("max")) {
            Object obj = extParams.get("max");
            if (obj != null) {
                if (obj instanceof BigDecimal) {
                    this.max = (BigDecimal) obj;
                } else if (obj instanceof Number) {
                    this.max = new BigDecimal(obj.toString());
                } else if (obj instanceof String) {
                    updateMax.accept((String) obj);
                } else {
                    log.warn("max 的类型不支持：{}", obj.getClass().getName());
                }
            } else {
                log.warn("max 值为 null，忽略");
            }
        }

        // 确保 min <= max
        if (this.min.compareTo(this.max) > 0) {
            log.warn("min ({}) > max ({})，交换值", this.min, this.max);
            BigDecimal temp = this.min;
            this.min = this.max;
            this.max = temp;
        }
    }
}