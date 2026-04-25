package io.github.ZeroFishKo.ZGuard.util;

import io.github.ZeroFishKo.ZGuard.annotation.XssMode;
import org.owasp.encoder.Encode;

import java.util.BitSet;

/**
 * 高并发场景下的高性能 XSS 清洗实用工具。
 * <p>
 * 此类提供两种 XSS 防护模式：
 * </p>
 * <ul>
 *   <li><b>黑名单模式</b> – 使用 {@link BitSet} 的危险字符快速过滤，
 *       结合全角到半角转换。执行时间通常低于 0.5 微秒。</li>
 *   <li><b>OWASP 编码模式</b> – 使用 OWASP Java Encoder 库进行全面的 HTML 编码，
 *       提供更强的 XSS 攻击防护。</li>
 * </ul>
 * <p>
 * 黑名单模式针对高吞吐量进行了优化，而 OWASP 模式提供更彻底的编码，但开销略高。
 * </p>
 *
 * @see XssMode
 */
public class XssCleanUtils {
    // 扩展的危险字符黑名单（包括全角等价字符）
    private static final BitSet BLACKLIST_BITSET = new BitSet(128);

    static {
        // 基本危险字符
        char[] blacklist = {'<', '>', '\'', '"', ';', '(', ')', '&', '%', '$', '\\', '/', '`', '*', '!', '#'};
        for (char c : blacklist) {
            BLACKLIST_BITSET.set(c);
        }
        // 全角危险字符（对应的半角：＜＞＇＂；（）＆％＄＼／）
        char[] fullWidthBlacklist = {'＜', '＞', '＇', '＂', '；', '（', '）', '＆', '％', '＄', '＼', '／'};
        for (char c : fullWidthBlacklist) {
            BLACKLIST_BITSET.set(c);
        }
    }

    /**
     * 使用默认的 {@link XssMode#BLACKLIST} 模式快速清洗值。
     * <p>
     * 这是一个便捷方法，等同于使用 {@code XssMode.BLACKLIST} 调用 {@link #fastClean(Object, XssMode)}。
     * </p>
     *
     * @param value 要清洗的值（可以是任何类型；仅处理 {@link String}）
     * @return 清洗后的值（如果不是字符串则返回原对象）
     */
    public static Object fastClean(Object value) {
        return fastClean(value, XssMode.BLACKLIST);
    }

    /**
     * 使用指定的 XSS 模式清洗值。
     * <p>
     * 如果值不是 {@link String}，则原样返回。
     * 对于字符串，根据所选模式执行清洗。
     * </p>
     *
     * @param value 要清洗的值
     * @param mode  清洗模式（不能为 {@code null}）
     * @return 清洗后的字符串，或原非字符串值
     */
    public static Object fastClean(Object value, XssMode mode) {
        if (!(value instanceof String)) {
            return value;
        }
        String input = (String) value;
        if (mode == XssMode.OWASP_ENCODE) {
            return Encode.forHtml(input);
        } else {
            return fastCleanBlacklist(input);
        }
    }

    /**
     * 基于黑名单的内部清洗逻辑。
     * <p>
     * 在单次遍历中执行以下转换：
     * </p>
     * <ul>
     *   <li>全角字符（U+FF01–U+FF5E）转换为半角等价字符。</li>
     *   <li>全角空格（U+3000）转换为普通空格（U+0020）。</li>
     *   <li>移除黑名单 {@link #BLACKLIST_BITSET} 中的字符。</li>
     * </ul>
     *
     * @param input 输入字符串
     * @return 清洗后的字符串
     */
    private static String fastCleanBlacklist(String input) {
        char[] chars = input.toCharArray();
        // 全角转半角 + 黑名单过滤（合并循环）
        int writePos = 0;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            // 全角转半角
            if (c == 12288) { // 全角空格
                c = 32; // 普通空格
            } else if (c >= 65281 && c <= 65374) { // 全角 ASCII 范围
                c = (char) (c - 65248);
            }
            // 过滤黑名单字符
            if (!BLACKLIST_BITSET.get(c)) {
                chars[writePos++] = c;
            }
        }
        return new String(chars, 0, writePos);
    }
}