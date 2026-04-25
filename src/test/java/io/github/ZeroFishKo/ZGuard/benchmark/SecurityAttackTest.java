package io.github.ZeroFishKo.ZGuard.benchmark;

import io.github.ZeroFishKo.ZGuard.annotation.XssMode;
import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.core.ValidateResult;
import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;
import io.github.ZeroFishKo.ZGuard.impl.LengthValidator;
import io.github.ZeroFishKo.ZGuard.impl.RangeValidator;
import io.github.ZeroFishKo.ZGuard.impl.RegexValidator;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.RiskAddrValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3SignatureValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;

import io.github.ZeroFishKo.ZGuard.util.XssCleanUtils;
import io.github.ZeroFishKo.ZGuard.util.hash.HashStrategy;
import io.github.ZeroFishKo.ZGuard.util.hash.HmacSha256Strategy;
import io.github.ZeroFishKo.ZGuard.util.hash.SimpleHashStrategy;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 安全攻击模拟测试套件
 * 包含：ReDoS、XSS绕过、哈希碰撞、超大字符串DoS、动态更新洪泛
 */
public class SecurityAttackTest {
    private static final HashStrategy hashStrategy = new SimpleHashStrategy();

    // 请求类（用于校验器绑定）
    public static class PaymentRequest {
        // 这里字段名要与校验器key对应
        public String walletAddr;
        public String paymentCode;
        public Double transferAmount;
        public String chainId;
        public String signature;
        public String comment; // 用于XSS测试
    }

    private static ZGuardValidator createValidator() {
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"walletAddr", "paymentCode", "chainId"};
            }

            @Override
            public XssMode getXssMode() {
                return XssMode.OWASP_ENCODE; // 启用 OWASP 编码
            }

            @Override
            public HashStrategy getHashStrategy() {
                return new HmacSha256Strategy(); // 切换为 HMAC-SHA256
            }
        };
        ZGuardValidator validator = new ZGuardValidator(config, "attack_salt");

        // 注册所有校验器（包括可能受攻击的）
        validator.registerValidators(Arrays.asList(
                new RequiredValidator(),
                new Web3WalletValidator(),
                new PaymentCodeValidator(),
                new RangeValidator(),
                new Web3ChainIdValidator(),
                new RiskAddrValidator(),
                new Web3SignatureValidator(),
                new RegexValidator(),   // 用于ReDoS测试
                new LengthValidator()
        ));

        // 为某些校验器配置参数（使攻击更容易触发）
        Map<String, Object> regexParams = new HashMap<>();
        regexParams.put("regex", "^(a+)+$"); // 经典ReDoS正则
        validator.dynamicUpdateValidatorParams("regex", regexParams);

        Map<String, Object> chainIdParams = new HashMap<>();
        chainIdParams.put("allowedChainIds", Arrays.asList("56", "97", "137", "1000"));
        validator.dynamicUpdateValidatorParams("web3ChainId", chainIdParams);

        validator.rebuildCombineSteps();
        return validator;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========== 安全攻击模拟测试开始 ==========\n");

        // 1. ReDoS 攻击测试
        testReDoS();

        // 2. XSS 绕过测试
        testXSSBypass();

        // 3. 哈希碰撞攻击
        testHashCollision();

        // 4. 超大字符串拒绝服务
        testLargeStringDoS();

        // 5. 动态更新洪泛攻击
        testDynamicUpdateFlood();

        System.out.println("\n========== 所有攻击测试完成 ==========");
    }

    // ==================== 测试方法 ====================

    private static void testReDoS() throws Exception {
        System.out.println("【1. ReDoS 攻击测试（带超时保护）】");
        RegexValidator regexValidator = new RegexValidator();
        Map<String, Object> params = new HashMap<>();
        params.put("regex", "^(a+)+$");
        long timeoutMs = 5; // 超时 5ms
        params.put("timeoutMs", timeoutMs);
        regexValidator.setExtParams(params);

        int count = 200;
        String attackString = repeatChar('a', count) + "b";
        System.out.println("攻击字符串长度: " + attackString.length());

        long start = System.nanoTime();
        boolean result = regexValidator.validate(attackString);
        long costUs = (System.nanoTime() - start) / 1000; // 微秒

        System.out.println("校验结果: " + result + "，耗时: " + costUs + " µs");
        if (!result && costUs > timeoutMs * 1000) { // 用实际超时比较
            System.out.println("✅ 超时保护生效，快速返回 false");
        } else {
            System.out.println("⚠️ 可能未触发超时");
        }
        System.out.println();
    }

    private static String repeatChar(char c, int times) {
        char[] chars = new char[times];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void testXSSBypass() {
        System.out.println("【2. XSS 绕过测试（使用 OWASP 编码）】");
        XssMode mode = XssMode.OWASP_ENCODE; // 与 validator 配置一致

        List<String> payloads = Arrays.asList(
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "javascript:alert(1)",
                "&#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;",
                "%3Cscript%3Ealert(1)%3C%2Fscript%3E",
                "＜script＞alert(1)＜/script＞",
                "\\u003Cscript\\u003Ealert(1)\\u003C/script\\u003E",
                "<a href=\"javascript:alert(1)\">click</a>"
        );

        for (String payload : payloads) {
            String cleaned = (String) XssCleanUtils.fastClean(payload, mode);
            // 简单判断：是否包含 < 或 >，OWASP 编码后会变成 &lt; 和 &gt;，因此不含 < 和 >
            boolean isSafe = !cleaned.contains("<") && !cleaned.contains(">");
            System.out.println("原始: " + payload);
            System.out.println("净化后: " + cleaned);
            System.out.println("是否安全: " + (isSafe ? "✅" : "❌ 可能被绕过"));
            System.out.println("---");
        }
        System.out.println();
    }

    private static void testHashCollision() {
        System.out.println("【3. 哈希碰撞攻击（使用 HMAC-SHA256）】");
        String salt = "attack_salt";
        HashStrategy hmacStrategy = new HmacSha256Strategy();

        // 碰撞演示（使用非地址字符串，仅展示哈希不同）
        String s1 = "Aa";
        String s2 = "BB";
        String h1 = hmacStrategy.hash(s1, salt);
        String h2 = hmacStrategy.hash(s2, salt);
        System.out.println("s1: " + s1 + ", 哈希: " + h1);
        System.out.println("s2: " + s2 + ", 哈希: " + h2);
        if (h1.equals(h2)) {
            System.out.println("✅ 碰撞成功！可用 s2 伪造 s1 的哈希");
        } else {
            System.out.println("⚠️ 未碰撞，需继续寻找");
        }
        System.out.println();

        // 防篡改校验测试（使用合法钱包地址）
        ZGuardValidator validator = createValidator(); // 已配置 HmacSha256Strategy
        String legalAddr = "0x1234567890abcdef1234567890abcdef12345678";
        String correctHash = hmacStrategy.hash(legalAddr, salt);
        String wrongHash = hmacStrategy.hash("0x0000000000000000000000000000000000000000", salt); // 另一个地址的哈希

        Map<String, Object> dataCorrect = new HashMap<>();
        dataCorrect.put("walletAddr", legalAddr);
        dataCorrect.put("walletAddr_hash", correctHash);
        dataCorrect.put("paymentCode", "12345678");
        dataCorrect.put("chainId", "56");
        dataCorrect.put("transferAmount", 500.0);
        validator.bindFieldValidators("walletAddr", Arrays.asList("required", "web3Wallet"));

        boolean isValidCorrect = validator.validate(dataCorrect).isSuccess();
        System.out.println("正确哈希校验结果（期望 true）: " + isValidCorrect);

        Map<String, Object> dataWrong = new HashMap<>(dataCorrect);
        dataWrong.put("walletAddr_hash", wrongHash);
        boolean isValidWrong = validator.validate(dataWrong).isSuccess();
        System.out.println("错误哈希校验结果（期望 false）: " + isValidWrong);
        System.out.println();
    }

    private static void testLargeStringDoS() {
        System.out.println("【4. 超大字符串拒绝服务】");
        ZGuardValidator validator = createValidator();

        // 构造超长字符串（10MB）
        int size = 10 * 1024 * 1024; // 10MB
        char[] chars = new char[size];
        Arrays.fill(chars, 'a');
        String hugeString = new String(chars);

        Map<String, Object> data = new HashMap<>();
        data.put("walletAddr", hugeString); // 超长地址（肯定非法）
        data.put("paymentCode", "12345678");
        data.put("chainId", "56");
        data.put("transferAmount", 500.0);

        // 测量耗时和内存
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long start = System.nanoTime();

        ValidateResult result = validator.validate(data);

        long cost = System.nanoTime() - start;
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        System.out.println("校验结果: " + result.isSuccess());
        System.out.println("耗时: " + cost / 1000 + " µs");
        System.out.println("内存增长: " + (memAfter - memBefore) / 1024 + " KB");
        System.out.println("如果耗时/内存异常大，说明存在DoS风险");
        System.out.println();
    }

    private static void testDynamicUpdateFlood() throws InterruptedException {
        System.out.println("【5. 动态更新洪泛攻击】");
        ZGuardValidator validator = createValidator();

        // 准备测试数据
        Map<String, Object> data = new HashMap<>();
        data.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        data.put("paymentCode", "12345678");
        data.put("chainId", "56");
        data.put("transferAmount", 500.0);

        // 使用 AtomicBoolean 控制线程
        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService updater = Executors.newSingleThreadExecutor();
        updater.submit(() -> {
            Random rand = new Random();
            while (running.get()) {
                try {
                    Thread.sleep(1); // 1ms 更新一次
                    Map<String, Object> params = new HashMap<>();
                    params.put("codeLength", rand.nextInt(10 - 4) + 4);
                    validator.dynamicUpdateValidatorParams("paymentCode", params);
                    validator.rebuildCombineSteps();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        // 执行大量校验，测量延迟分布
        int count = 10000;
        long[] latencies = new long[count];
        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            validator.validate(data);
            latencies[i] = System.nanoTime() - start;
        }
        running.set(false);
        updater.shutdownNow();

        // 统计延迟
        Arrays.sort(latencies);
        long avg = Arrays.stream(latencies).sum() / count;
        long p50 = latencies[(int) (count * 0.5)];
        long p99 = latencies[(int) (count * 0.99)];
        long p999 = latencies[(int) (count * 0.999)];

        System.out.println("平均延迟: " + avg / 1000 + " µs");
        System.out.println("50% 延迟: " + p50 / 1000 + " µs");
        System.out.println("99% 延迟: " + p99 / 1000 + " µs");
        System.out.println("99.9% 延迟: " + p999 / 1000 + " µs");
        System.out.println("如果与无更新时相比显著升高，说明动态更新影响性能");
        System.out.println();
    }
}