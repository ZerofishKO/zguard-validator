package io.github.ZeroFishKoZGuard.config;

import io.github.ZeroFishKoZGuard.annotation.FailureMode;
import io.github.ZeroFishKoZGuard.annotation.XssMode;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;
import io.github.ZeroFishKoZGuard.util.hash.HmacSha256Strategy;
import io.github.ZeroFishKoZGuard.util.hash.HashStrategy;
import io.github.ZeroFishKoZGuard.util.hash.SimpleHashStrategy;

/**
 * 校验框架的通用配置接口。
 * <p>
 * 此接口定义了校验器的所有可配置行为。
 * 它设计为由用户实现，以定制框架以适应高并发生产场景和嵌入式测试。
 * 所有方法都有合理的默认值，以最小化配置开销。
 * </p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * ValidatorConfig config = new ValidatorConfig() {
 *     @Override
 *     public String[] getCoreFields() {
 *         return new String[]{"walletAddr", "amount"};
 *     }
 *
 *     @Override
 *     public long getRegexTimeoutMs() {
 *         return 500; // 为复杂正则增加超时
 *     }
 * };
 * ZGuardValidator validator = new ZGuardValidator(config, "mySalt");
 * }</pre>
 *
 * @see ZGuardValidator
 */
public interface ValidatorConfig {

    /**
     * 确定批量校验是否应在第一次失败时立即停止。
     * <p>
     * 当启用时（默认），一旦有任何项校验失败，批量校验过程将终止，
     * 所有剩余项将报告为失败，并带有特殊的 "batch_fast_fail" 错误。
     * 这对于希望尽早失败以节省资源的高吞吐场景很有用。
     * </p>
     *
     * @return {@code true} 启用批量校验快速失败，{@code false} 校验所有项
     */
    default boolean isBatchFastFail() {
        return true;
    }

    /**
     * 定义必须在每个校验请求中存在且非空的核心字段名称列表。
     * <p>
     * 核心字段在任何校验器执行之前进行检查。如果缺少任何核心字段，
     * 校验立即失败并返回特定错误。这确保关键数据始终被提供。
     * </p>
     *
     * @return 核心字段名称数组（默认为空数组）
     */
    default String[] getCoreFields() {
        return new String[0];
    }

    /**
     * 确定单个字段的组合校验是否应在第一个校验器失败后停止（快速失败）
     * 还是继续执行所有校验器。
     * <p>
     * 当启用时（默认），如果字段的某个校验器返回 {@code false}，
     * 该字段后续的校验器将被跳过。当禁用时，无论失败与否，所有校验器都将执行，
     * 从而允许收集所有错误消息。
     * </p>
     *
     * @return {@code true} 启用字段内校验器链的快速失败，
     *         {@code false} 执行所有校验器
     */
    default boolean isCombineFastFail() {
        return true;
    }

    /**
     * 返回校验器的失败模式。
     * <p>
     * 此设置确定校验是应在第一个错误处停止（快速失败）还是继续收集所有错误。
     * 目前仅完全支持 {@link FailureMode#QUICK_FAIL}；{@link FailureMode#COLLECT_ALL} 预留未来扩展。
     * </p>
     *
     * @return 失败模式（默认：{@link FailureMode#QUICK_FAIL}）
     */
    default FailureMode getFailureMode() {
        return FailureMode.QUICK_FAIL;
    }

    /**
     * 动态配置更新后等待重建校验步骤的延迟（毫秒）。
     * <p>
     * 此延迟确保在新配置应用之前所有参数注入已完全传播。
     * 它有助于防止高并发环境中的竞争条件。
     * </p>
     *
     * @return 延迟毫秒数（默认：100）
     */
    default long getUpdateDelayMs() {
        return 100;
    }

    /**
     * 控制框架是否应记录 XSS 清洗操作的结果。
     * <p>
     * 启用此功能可能会产生大量日志，但可用于调试或审计与 XSS 相关的转换。
     * 建议在生产环境中保持禁用。
     * </p>
     *
     * @return {@code true} 记录 XSS 清洗详情，{@code false} 否则（默认）
     */
    default boolean isLogXssClean() {
        return false;
    }

    /**
     * 返回应用于字符串值的 XSS 清洗模式。
     * <p>
     * 两种模式可用：
     * </p>
     * <ul>
     *   <li>{@link XssMode#BLACKLIST} – 快速基于字符的过滤（默认）</li>
     *   <li>{@link XssMode#OWASP_ENCODE} – 使用 OWASP Encoder 进行全面的 HTML 编码</li>
     * </ul>
     * <p>
     * OWASP 模式提供更强的保护，但开销略高。
     * </p>
     *
     * @return XSS 清洗模式（默认：{@link XssMode#BLACKLIST}）
     */
    default XssMode getXssMode() {
        return XssMode.BLACKLIST;
    }

    /**
     * 返回用于防篡改校验的哈希策略。
     * <p>
     * 哈希策略定义了当存在 {@code _hash} 字段时框架如何计算字段值的期望哈希。
     * 提供了两种策略：
     * </p>
     * <ul>
     *   <li>{@link SimpleHashStrategy} – 快速但密码学上弱</li>
     *   <li>{@link HmacSha256Strategy} – 安全的 HMAC-SHA256</li>
     * </ul>
     * <p>
     * 用户也可以实现自定义策略。
     * </p>
     *
     * @return 哈希策略（默认：{@link SimpleHashStrategy}）
     */
    default HashStrategy getHashStrategy() {
        return new SimpleHashStrategy();
    }

    /**
     * 返回正则校验的默认超时时间（毫秒）。
     * <p>
     * 如果正则校验超过此超时，将被中止并视为失败。
     * 这可以防止 ReDoS（正则表达式拒绝服务）攻击。
     * 值为 0 表示禁用超时。
     * </p>
     *
     * @return 超时毫秒数（默认：100）
     */
    default long getRegexTimeoutMs() {
        return 100;
    }
}