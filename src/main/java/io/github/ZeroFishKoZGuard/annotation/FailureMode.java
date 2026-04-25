package io.github.ZeroFishKoZGuard.annotation;

/**
 * 校验失败处理模式。
 */
public enum FailureMode {
    /**
     * 在第一次失败时立即停止校验
     */
    QUICK_FAIL,
    /**
     * 收集所有字段的所有错误（预留未来使用）
     */
    COLLECT_ALL
}