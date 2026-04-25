package io.github.ZeroFishKo.ZGuard.impl.web3;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Web3 钱包地址校验器（例如以太坊地址）。
 * <p>
 * 此校验器确保地址是一个 42 字符的字符串，以 "0x" 开头，
 * 后跟 40 个十六进制字符（0-9, a-f, A-F）。它不执行校验和验证（例如 EIP-55），
 * 但如有需要可以扩展。
 * </p>
 *
 * <p>通过使用有效字符的 {@link Set} 以及提前的长度/前缀检查来优化性能。</p>
 *
 * @see ValidatorHandler
 */
public class Web3WalletValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(Web3WalletValidator.class);
    private static final int VALID_LENGTH = 42;

    // 优化：移除了 x/X，只保留 0-9, a-f, A-F（前缀已验证）
    private static final Set<Character> VALID_CHARS;

    static {
        Set<Character> tempChars = new HashSet<>();
        // 0-9
        for (char c = '0'; c <= '9'; c++) tempChars.add(c);
        // a-f
        for (char c = 'a'; c <= 'f'; c++) tempChars.add(c);
        // A-F
        for (char c = 'A'; c <= 'F'; c++) tempChars.add(c);
        VALID_CHARS = Collections.unmodifiableSet(tempChars);
    }

    /**
     * 校验 Web3 钱包地址。
     *
     * @param value 地址字符串
     * @return 如果地址有效返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null || !(value instanceof String)) {
            log.debug("钱包地址校验：值为 null 或不是字符串，value={}", value);
            return false;
        }
        String addr = (String) value;

        log.debug("钱包地址校验：address={}, length={}（期望 42）", addr, addr.length());
        if (addr.length() != VALID_LENGTH) {
            log.debug("钱包地址校验失败：长度不匹配，实际={}，期望={}", addr.length(), VALID_LENGTH);
            return false;
        }
        if (addr.charAt(0) != '0' || (addr.charAt(1) != 'x' && addr.charAt(1) != 'X')) {
            log.debug("钱包地址校验失败：缺少 0x 前缀");
            return false;
        }
        for (int i = 2; i < VALID_LENGTH; i++) {
            if (!VALID_CHARS.contains(addr.charAt(i))) {
                log.debug("钱包地址校验失败：位置 {} 的字符无效，字符={}", i, addr.charAt(i));
                return false;
            }
        }
        log.debug("钱包地址校验成功：{}", addr);
        return true;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 描述所需格式的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "无效的 Web3 钱包地址（必须是 42 字符的十六进制字符串，以 0x 开头）";
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
     * @return "web3Wallet"
     */
    @Override
    public String getValidatorKey() {
        return "web3Wallet";
    }
}