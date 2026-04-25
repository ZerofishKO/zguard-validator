package io.github.ZeroFishKoZGuard.util;

import io.github.ZeroFishKoZGuard.core.ZGuardValidator;

/**
 * 用于检测当前 Java Development Kit (JDK) 版本的实用工具。
 * <p>
 * 此类提供了简单的谓词，用于确定运行时环境是 JDK 8、JDK 17 还是 JDK 21 或更高版本。
 * 框架内部使用它来根据可用的 JDK 特性调整行为（例如启用虚拟线程）。
 * </p>
 *
 * <p>版本检测逻辑：</p>
 * <ul>
 *   <li>对于 JDK 8 及更早版本，系统属性 {@code java.version} 以 {@code "1."} 开头
 *       （例如 {@code "1.8.0_201"}）。解析后提取主版本号 {@code 8}。</li>
 *   <li>对于 JDK 9 及更高版本，属性模式类似 {@code "11.0.12"} 或 {@code "17.0.1"}，
 *       因此取第一个点之前的部分作为主版本号。</li>
 * </ul>
 *
 * <p>所有方法都是线程安全的。</p>
 *
 * @see ZGuardValidator#batchValidate(java.util.List)
 * @see VirtualThreadExecutor
 */
public class JdkVersionUtils {
    private static final int JDK_VERSION;

    static {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            // JDK 8 或更早："1.8.0_201" -> "8"
            version = version.substring(2, 3);
        } else {
            // JDK 9 或更高："11.0.12" -> "11"
            int dotIndex = version.indexOf('.');
            version = dotIndex > 0 ? version.substring(0, dotIndex) : version;
        }
        JDK_VERSION = Integer.parseInt(version);
    }

    /**
     * 检查当前 JDK 版本是否为 8。
     *
     * @return 如果运行环境是 JDK 8 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isJdk8() {
        return JDK_VERSION == 8;
    }

    /**
     * 检查当前 JDK 版本是否为 17。
     *
     * @return 如果运行环境是 JDK 17 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isJdk17() {
        return JDK_VERSION == 17;
    }

    /**
     * 检查当前 JDK 版本是否为 21 或更高。
     * <p>
     * 此方法用于确定虚拟线程（JDK 21 引入）是否可用。
     * </p>
     *
     * @return 如果运行环境是 JDK 21 或更高返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isJdk21OrHigher() {
        return JDK_VERSION >= 21;
    }
}