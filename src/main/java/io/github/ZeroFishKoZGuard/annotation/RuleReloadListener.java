package io.github.ZeroFishKoZGuard.annotation;

/**
 * 规则重载生命周期监听器
 */
@FunctionalInterface
public interface RuleReloadListener {
    void onAfterReload();

    // 可扩展：beforeReload，当前只提供了 after
    default void onBeforeReload() {
    }
}