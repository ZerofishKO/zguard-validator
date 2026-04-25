package io.github.ZeroFishKo.ZGuard.util.hash;

import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;

/**
 * 用于防篡改校验的哈希计算策略接口。
 * <p>
 * 此接口的实现定义了如何从输入字符串和盐值派生哈希值。
 * 框架使用配置的策略计算期望的哈希值，并与数据映射中提供的 {@code _hash} 字段进行比较。
 * </p>
 *
 * <p>此设计允许用户在不同的哈希算法之间进行选择：</p>
 * <ul>
 *   <li>{@link SimpleHashStrategy} – 快速、非加密哈希（类似于 Java 的 {@code String.hashCode()}，但添加了盐值）。
 *       适用于对性能敏感且安全性不是首要考虑的场景。</li>
 *   <li>{@link HmacSha256Strategy} – 安全的 HMAC-SHA256 哈希，提供强抗碰撞性和篡改检测。
 *       推荐用于生产环境的安全要求。</li>
 * </ul>
 *
 * <p>用户也可以针对特定需求实现自定义策略。</p>
 *
 * @see ZGuardValidator#validate(java.util.Map)
 */
public interface HashStrategy {

    /**
     * 计算输入字符串与给定盐值组合的哈希值。
     * <p>
     * 实现必须是确定性的：相同的输入和盐值必须始终产生相同的哈希值。
     * 同时应当高效，因为在校验过程中可能会被频繁调用。
     * </p>
     *
     * @param input 要进行哈希的原始值（永远不为 {@code null}）
     * @param salt  用于防止预计算字典攻击的保密盐值（永远不为 {@code null}）
     * @return 哈希值，可以是十六进制字符串或十进制字符串；
     *         格式必须一致，以便在 {@link ZGuardValidator} 中进行比较
     */
    String hash(String input, String salt);
}