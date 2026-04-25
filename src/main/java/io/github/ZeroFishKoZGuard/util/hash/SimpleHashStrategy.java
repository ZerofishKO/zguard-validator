package io.github.ZeroFishKoZGuard.util.hash;

/**
 * {@link HashStrategy} 的简单非加密哈希实现。
 * <p>
 * 此策略使用与 Java 的 {@code String.hashCode()} 相同的算法计算哈希，但添加了盐值。
 * 速度快，但不适用于安全敏感的场景。输出为十进制字符串。
 * </p>
 */
public class SimpleHashStrategy implements HashStrategy {
    @Override
    public String hash(String input, String salt) {
        int hash = 17;
        for (char c : (input + salt).toCharArray()) {
            hash = hash * 31 + c;
        }
        return Integer.toString(hash);
    }
}