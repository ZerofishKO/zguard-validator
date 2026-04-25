package io.github.ZeroFishKoZGuard.util.hash;

/**
 * {@link HashStrategy} 的 HMAC-SHA256 实现。
 * <p>
 * 此策略使用 HmacSHA256 算法生成安全、抗碰撞的哈希值。
 * 输出为 64 个字符的十六进制字符串。
 * </p>
 *
 * @see javax.crypto.Mac
 */
public class HmacSha256Strategy implements HashStrategy {
    @Override
    public String hash(String input, String salt) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(salt.getBytes(), "HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(input.getBytes());
            return bytesToHex(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 错误", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}