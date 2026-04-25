package io.github.ZeroFishKoZGuard.annotation;

import io.github.ZeroFishKoZGuard.config.ValidatorConfig;
import io.github.ZeroFishKoZGuard.util.XssCleanUtils;

/**
 * 定义 XSS（跨站脚本）清洗的可用模式。
 * <p>
 * 框架提供了两种策略：
 * </p>
 * <ul>
 *   <li>{@link #BLACKLIST} – 使用危险字符黑名单进行快速字符过滤。
 *       此模式针对高吞吐量和最小开销进行了优化。</li>
 *   <li>{@link #OWASP_ENCODE} – 使用 OWASP Java Encoder 库进行全面的 HTML 编码。
 *       此模式通过转义所有 HTML 元字符提供更强的保护。</li>
 * </ul>
 * <p>
 * 模式可通过 {@link ValidatorConfig#getXssMode()} 配置。
 * </p>
 *
 * @see XssCleanUtils
 */
public enum XssMode {
    /**
     * 快速黑名单过滤（默认）。
     */
    BLACKLIST,

    /**
     * 用于最高安全性的 OWASP HTML 编码。
     */
    OWASP_ENCODE
}