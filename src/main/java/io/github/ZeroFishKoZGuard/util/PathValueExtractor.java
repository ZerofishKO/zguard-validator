package io.github.ZeroFishKoZGuard.util;

import io.github.ZeroFishKoZGuard.core.ZGuardValidator;

import java.util.List;
import java.util.Map;

/**
 * 使用点分隔路径从嵌套 {@link Map} 结构中提取值的实用工具。
 * <p>
 * 此类支持类似 {@code "a.b.c"} 的路径来遍历嵌套映射。
 * 还支持使用方括号进行数组/列表索引，例如 {@code "items[0].name"}。
 * </p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * Map<String, Object> root = new HashMap<>();
 * Map<String, Object> a = new HashMap<>();
 * List<Map<String, Object>> b = new ArrayList<>();
 * Map<String, Object> item = new HashMap<>();
 * item.put("c", "value");
 * b.add(item);
 * a.put("b", b);
 * root.put("a", a);
 *
 * Object result = PathValueExtractor.getValue(root, "a.b[0].c"); // 返回 "value"
 * }</pre>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>数组/列表索引必须是非负整数且在边界内。</li>
 *   <li>如果路径的任何部分不存在或类型错误，返回 {@code null}。</li>
 *   <li>此类不支持在键中转义点或方括号。</li>
 * </ul>
 *
 * @see ZGuardValidator#validate(Map)
 */
public final class PathValueExtractor {

    private PathValueExtractor() {
        // 私有构造器，防止实例化
    }

    /**
     * 使用点分隔路径从嵌套 {@link Map} 中提取值。
     * <p>
     * 路径可以包含使用方括号的数组/列表索引。
     * 例如，{@code "users[0].profile.age"} 将首先导航到 "users" 键，
     * 然后将其视为列表并取第一个元素，然后导航到 "profile"，再取 "age"。
     * </p>
     *
     * @param root 根映射；可能为 {@code null}
     * @param path 点分隔路径；可能为 {@code null} 或空
     * @return 提取的值，如果路径无效或任何部分缺失则返回 {@code null}
     */
    public static Object getValue(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            // 处理类似 "items[0]" 的数组索引
            String fieldName = part;
            int idx = -1;
            if (part.endsWith("]")) {
                int lb = part.indexOf('[');
                if (lb > 0) {
                    fieldName = part.substring(0, lb);
                    String idxStr = part.substring(lb + 1, part.length() - 1);
                    try {
                        idx = Integer.parseInt(idxStr);
                    } catch (NumberFormatException e) {
                        return null; // 非法索引
                    }
                }
            }

            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(fieldName);
            if (current == null) {
                return null;
            }

            // 如果有索引，则从当前值中提取索引元素
            if (idx >= 0) {
                if (current instanceof List) {
                    List<?> list = (List<?>) current;
                    if (idx >= 0 && idx < list.size()) {
                        current = list.get(idx);
                    } else {
                        return null;
                    }
                } else if (current.getClass().isArray()) {
                    // 通过反射处理数组
                    int length = java.lang.reflect.Array.getLength(current);
                    if (idx >= 0 && idx < length) {
                        current = java.lang.reflect.Array.get(current, idx);
                    } else {
                        return null;
                    }
                } else {
                    return null; // 不是集合/数组，无法索引
                }
            }
        }
        return current;
    }
}