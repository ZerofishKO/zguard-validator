package io.github.ZeroFishKo.ZGuard.impl.Payment;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKo.ZGuard.annotation.YmlConfigBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 支付验证码校验器（例如短信验证码、OTP）。
 * <p>
 * 此校验器确保输入是正确长度的字符串且仅包含数字。
 * 所需长度可通过 {@link #setExtParams(Map)} 或 YAML 绑定动态配置。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * validatorParams:
 *   paymentCode:
 *     codeLength: 8
 * </pre>
 *
 * @see ValidatorHandler
 * @see YmlConfigBinding
 */
@YmlConfigBinding(configPath = "validatorParams.paymentCode")
public class PaymentCodeValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentCodeValidator.class);
    private int codeLength = 6; // 默认 6 位验证码

    /**
     * 校验支付码。
     *
     * @param value 支付码字符串
     * @return 如果代码非空、长度符合预期且仅包含数字返回 {@code true}；
     *         否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null || !(value instanceof String)) {
            log.info("PaymentCodeValidator 校验：值为 null 或不是字符串，返回 false");
            return false;
        }
        String code = (String) value;
        log.info("PaymentCodeValidator 校验：code={}, 期望长度={}, 实际长度={}",
                code, this.codeLength, code.length());
        if (code.length() != this.codeLength) {
            log.info("PaymentCodeValidator 校验：长度不匹配，返回 false");
            return false;
        }
        for (char c : code.toCharArray()) {
            if (!Character.isDigit(c)) {
                log.info("PaymentCodeValidator 校验：包含非数字字符 '{}'，返回 false", c);
                return false;
            }
        }
        log.info("PaymentCodeValidator 校验：通过");
        return true;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 指示所需代码长度的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "无效的支付码（必须是 " + codeLength + " 位数字）";
    }


    @Override
    public Object[] getMessageArguments() {
        return new Object[]{codeLength};
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
     * @return "paymentCode"
     */
    @Override
    public String getValidatorKey() {
        return "paymentCode";
    }

    /**
     * 返回当前配置的代码长度。
     *
     * @return 代码长度
     */
    public int getCodeLength() {
        return this.codeLength;
    }

    /**
     * 动态更新代码长度。
     * <p>
     * 期望一个包含键 {@code "codeLength"} 的映射。
     * 值可以是 {@link Integer} 或可解析的 {@link String}。
     * 如果值无效，当前长度不会更改。
     * </p>
     *
     * @param extParams 包含新代码长度的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        log.debug("PaymentCodeValidator 接收到参数：{}", extParams);
        if (extParams != null && extParams.containsKey("codeLength")) {
            Object codeLengthObj = extParams.get("codeLength");
            if (codeLengthObj instanceof Integer) {
                this.codeLength = (Integer) codeLengthObj;
            } else if (codeLengthObj instanceof String) {
                try {
                    this.codeLength = Integer.parseInt((String) codeLengthObj);
                } catch (NumberFormatException e) {
                    log.error("codeLength 类型转换失败", e);
                    return;
                }
            }
            log.info("PaymentCodeValidator 更新 codeLength：{}（原默认值：6）", this.codeLength);
        }
    }


}