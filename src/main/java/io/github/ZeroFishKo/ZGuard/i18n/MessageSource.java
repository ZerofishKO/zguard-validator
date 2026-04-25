package io.github.ZeroFishKo.ZGuard.i18n;

import java.util.Locale;

/**
 * 用于国际化的简单消息源接口。
 */
public interface MessageSource {
    /**
     * 获取给定键和区域设置的消息，使用提供的参数。
     *
     * @param key    消息键
     * @param args   用于填充占位符的参数（例如 {0}、{1}）
     * @param locale 目标区域设置
     * @return 本地化消息，如果未找到则返回键本身
     */
    String getMessage(String key, Object[] args, Locale locale);
}