package io.github.ZeroFishKo.ZGuard.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用 Java 的 ResourceBundle 并支持 UTF-8 的 MessageSource 实现。
 * <p>
 * 基础名称默认为 "messages"。可通过构造器设置。
 * 属性文件应保存为 UTF-8 编码（不带 BOM）。
 * 当请求的区域没有资源文件时，会回退到基础名称（例如 messages.properties），
 * 而不是系统默认区域。
 * </p>
 */
public class ResourceBundleMessageSource implements MessageSource {
    private final String baseName;
    private final ConcurrentHashMap<String, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    private final ResourceBundle.Control utf8Control = new UTF8Control();

    public ResourceBundleMessageSource() {
        this("messages");
    }

    public ResourceBundleMessageSource(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public String getMessage(String key, Object[] args, Locale locale) {
        try {
            ResourceBundle bundle = bundleCache.computeIfAbsent(
                    locale.toString() + "@" + baseName,
                    k -> ResourceBundle.getBundle(baseName, locale, utf8Control)
            );
            String pattern = bundle.getString(key);
            if (args == null || args.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            return key; // 回退到键本身
        }
    }

    /**
     * 自定义的 ResourceBundle.Control，使用 UTF-8 编码读取属性文件，
     * 并阻止回退到系统默认区域。
     */
    private static class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload) throws IOException, IllegalAccessException, InstantiationException {
            if (!"java.properties".equals(format)) {
                return super.newBundle(baseName, locale, format, loader, reload);
            }
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            try (InputStream is = loader.getResourceAsStream(resourceName)) {
                if (is == null) return null;
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return new PropertyResourceBundle(reader);
                }
            }
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            // 阻止回退到系统默认区域
            return null;
        }
    }
}