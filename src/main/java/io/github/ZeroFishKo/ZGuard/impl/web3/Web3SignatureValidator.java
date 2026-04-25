package io.github.ZeroFishKo.ZGuard.impl.web3;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Web3 签名校验器（例如以太坊签名）。
 * <p>
 * 此校验器检查签名字符串的格式。它验证：
 * </p>
 * <ul>
 *   <li>长度符合标准长度之一（130、132、193）。</li>
 *   <li>以 "0x" 或 "0X" 开头。</li>
 *   <li>剩余字符为十六进制字符。</li>
 *   <li>最后两个十六进制数字（V 值）是 {0, 1, 27, 28} 之一。</li>
 * </ul>
 *
 * <p>此校验器不执行签名的密码学验证，仅检查结构有效性。</p>
 *
 * @see ValidatorHandler
 */
public class Web3SignatureValidator implements ValidatorHandler {
    private Set<Integer> validLengths;
    private Set<Integer> validVValues;

    public Web3SignatureValidator() {
        Set<Integer> lengthSet = new HashSet<>();
        lengthSet.add(130);
        lengthSet.add(132);
        lengthSet.add(193);
        this.validLengths = Collections.unmodifiableSet(lengthSet);

        Set<Integer> vValueSet = new HashSet<>();
        vValueSet.add(0);
        vValueSet.add(1);
        vValueSet.add(27);
        vValueSet.add(28);
        this.validVValues = Collections.unmodifiableSet(vValueSet);
    }

    /**
     * 校验 Web3 签名字符串。
     *
     * @param value 签名字符串
     * @return 如果签名满足所有格式标准返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null || !(value instanceof String)) return false;
        String signature = (String) value;

        // 先检查长度，快速失败
        if (!validLengths.contains(signature.length())) return false;
        // 前缀检查
        if (!signature.startsWith("0x") && !signature.startsWith("0X")) return false;

        String pureSig = signature.substring(2);
        // 十六进制格式检查
        for (char c : pureSig.toCharArray()) {
            if (Character.digit(c, 16) == -1) {
                return false;
            }
        }

        // V 值检查（Web3 标准）
        if (signature.length() >= 130) {
            String vStr = pureSig.substring(pureSig.length() - 2);
            try {
                int v = Integer.parseInt(vStr, 16);
                if (!validVValues.contains(v)) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 解释所需格式的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "无效的 Web3 签名（必须以 0x 开头、长度正确、十六进制、V 值有效）";
    }

    /**
     * 此校验器的优先级（1 = 最高）。
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
     * @return "web3Signature"
     */
    @Override
    public String getValidatorKey() {
        return "web3Signature";
    }
}