package io.github.ZeroFishKo.ZGuard.impl.web3;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Web3 金额校验器，确保金额为正且小数位数在允许范围内。
 * <p>
 * 此校验器用于检查交易金额、代币转账等。
 * 允许的最大小数位数可通过 {@link #setExtParams(Map)} 或 YAML 绑定配置。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * validatorParams:
 *   web3Amount:
 *     maxPrecision: 18
 * </pre>
 *
 * @see ValidatorHandler
 */
public class Web3AmountValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(Web3ChainIdValidator.class);
    private int maxPrecision = 18; // 许多代币的默认精度（例如 ETH、BSC）

    /**
     * 校验 Web3 金额。
     * <p>
     * 输入可以是 {@link Number} 或 {@link String}。会被转换为 {@link BigDecimal}。
     * 校验通过的条件：
     * </p>
     * <ul>
     *   <li>金额大于零。</li>
     *   <li>小数位数（{@link BigDecimal#scale()}）不超过 {@code maxPrecision}。</li>
     * </ul>
     *
     * @param value 金额（Number 或 String）
     * @return 如果有效返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null) return false;
        BigDecimal amount;
        try {
            if (value instanceof Number) {
                amount = new BigDecimal(value.toString());
            } else if (value instanceof String) {
                amount = new BigDecimal((String) value);
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        return amount.scale() <= maxPrecision;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 指示金额必须为正且小数位数不超过最大精度的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "无效的 Web3 金额（必须为正数，小数位数 ≤ " + maxPrecision + "）";
    }

    @Override
    public Object[] getMessageArguments() {
        return new Object[]{maxPrecision};
    }

    /**
     * 此校验器的优先级（2 = 高）。
     *
     * @return 优先级值
     */
    @Override
    public int getPriority() {
        return 2;
    }

    /**
     * 此校验器的唯一键。
     *
     * @return "web3Amount"
     */
    @Override
    public String getValidatorKey() {
        return "web3Amount";
    }

    /**
     * 动态更新允许的最大小数位数。
     * <p>
     * 期望一个包含键 {@code "maxPrecision"} 和整数值的映射。
     * 如果提供的值不是正数，则使用默认值（18）。
     * </p>
     *
     * @param extParams 包含新 maxPrecision 的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        if (extParams != null && extParams.containsKey("maxPrecision")) {
            Object value = extParams.get("maxPrecision");
            if (value instanceof Integer) {
                this.maxPrecision = (Integer) value;
            } else if (value instanceof String) {
                try {
                    this.maxPrecision = Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    log.error("maxPrecision 格式无效", e);
                    return;
                }
            } else if (value instanceof Number) {
                this.maxPrecision = ((Number) value).intValue();
            }
            if (this.maxPrecision <= 0) {
                this.maxPrecision = 18; // 回退到默认值
            }
        }
    }
}