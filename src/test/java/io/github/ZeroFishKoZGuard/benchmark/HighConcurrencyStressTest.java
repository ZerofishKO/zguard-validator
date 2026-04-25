package io.github.ZeroFishKoZGuard.benchmark;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.ValidateWith;
import io.github.ZeroFishKoZGuard.config.ValidatorConfig;
import io.github.ZeroFishKoZGuard.core.YmlConfigBinder;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;
import io.github.ZeroFishKoZGuard.core.ValidateResult;
import io.github.ZeroFishKoZGuard.impl.RequiredValidator;
import io.github.ZeroFishKoZGuard.impl.RangeValidator;
import io.github.ZeroFishKoZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKoZGuard.impl.web3.RiskAddrValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.ZeroFishKoZGuard.util.VirtualThreadExecutor.createExecutor;

public class HighConcurrencyStressTest {

    private static final Logger log = LoggerFactory.getLogger(HighConcurrencyStressTest.class);

    // 请求类定义（必须与 YML 配置的字段绑定规则一致）
    public static class PaymentRequest {
        @ValidateWith({"required", "web3Wallet", "riskAddr"})
        public String walletAddr;

        @ValidateWith({"required", "paymentCode"})
        public String paymentCode;

        @ValidateWith({"required", "range"})
        public Double transferAmount;

        @ValidateWith({"required", "web3ChainId"})
        public String chainId;
    }

    public static void main(String[] args) throws Exception {
        // -------------------- 1. 初始化校验器 --------------------
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"walletAddr", "paymentCode", "chainId"};
            }
        };

        ZGuardValidator validator = new ZGuardValidator(config, "stress_salt");

        List<ValidatorHandler> handlers = Arrays.asList(
                new RequiredValidator(),
                new Web3WalletValidator(),
                new PaymentCodeValidator(),
                new RangeValidator(),
                new Web3ChainIdValidator(),
                new RiskAddrValidator()
        );
        validator.registerValidators(handlers);
        validator.bindAnnotations(PaymentRequest.class);

        // -------------------- 2. 加载 YML 配置并绑定参数 --------------------
        YmlConfigBinder configBinder = new YmlConfigBinder();
        try (InputStream ymlStream = HighConcurrencyStressTest.class.getClassLoader()
                .getResourceAsStream("validator-rules.yml")) {
            if (ymlStream == null) {
                throw new RuntimeException("未找到 validator-rules.yml 文件，请确保它在 classpath 根目录");
            }
            configBinder.loadYml(ymlStream);
            configBinder.bindAllValidators(validator.getValidatorRegistry());
        }
        // 手动重建组合步骤，确保参数生效（bindAllValidators 内部不会自动重建）
        validator.rebuildCombineSteps();

        log.info("校验器初始化完成，已加载 YML 配置");

        // -------------------- 3. 生成数据池（符合 YML 规则） --------------------
        int poolSize = 100_000;          // 数据池大小
        double validRatio = 0.8;          // 有效数据比例（与 YML 规则一致）

        List<Map<String, Object>> dataPool = new ArrayList<>(poolSize);
        // 从 YML 配置中读取允许的链ID（也可以硬编码，但这里手动读取）
        List<String> allowedChains = Arrays.asList("56", "97", "1000", "137"); // 与 YML 一致

        for (int i = 0; i < poolSize; i++) {
            Map<String, Object> data = new HashMap<>();
            // 随机决定本条数据是否有效
            boolean isValid = ThreadLocalRandom.current().nextDouble() < validRatio;

            // 钱包地址：有效地址始终使用随机有效地址
            data.put("walletAddr", TestDataGenerator.randomValidAddress());

            if (isValid) {
                // 有效数据：所有字段均符合规则
                data.put("paymentCode", TestDataGenerator.randomValidPaymentCode(8)); // 8 位数字
                data.put("chainId", allowedChains.get(ThreadLocalRandom.current().nextInt(allowedChains.size())));
                // 金额在 100.0 ~ 100000.0 之间（含边界）
                double amount = 100.0 + ThreadLocalRandom.current().nextDouble(99900.0);
                data.put("transferAmount", Math.round(amount * 100) / 100.0); // 保留两位小数
            } else {
                // 无效数据：随机破坏一个字段
                int errorType = ThreadLocalRandom.current().nextInt(3); // 0,1,2
                switch (errorType) {
                    case 0: // 支付码错误
                        data.put("paymentCode", "12345a"); // 非纯数字或长度不对
                        data.put("chainId", allowedChains.get(0)); // 链ID有效
                        data.put("transferAmount", 5000.0);
                        break;
                    case 1: // 链ID错误
                        data.put("paymentCode", TestDataGenerator.randomValidPaymentCode(8));
                        data.put("chainId", "999"); // 不在白名单
                        data.put("transferAmount", 5000.0);
                        break;
                    case 2: // 金额错误（超出范围）
                        data.put("paymentCode", TestDataGenerator.randomValidPaymentCode(8));
                        data.put("chainId", allowedChains.get(0));
                        data.put("transferAmount", 200000.0); // 大于 100000
                        break;
                }
            }
            dataPool.add(data);
        }

        log.info("数据池生成完成，大小：{}，有效比例：{}", poolSize, validRatio);

        // -------------------- 4. 压力测试参数 --------------------
        int totalRequests = 1_000_000;          // 总请求数
        int threadCount = 2000;                  // 虚拟线程数（JDK 21+）
        ExecutorService executor = createExecutor();

        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicLong okCount = new AtomicLong();
        AtomicLong failCount = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        // -------------------- 5. 提交任务 --------------------
        long start = System.nanoTime();
        for (int i = 0; i < totalRequests; i++) {
            int index = i % poolSize; // 循环取数据
            Map<String, Object> data = dataPool.get(index);
            executor.submit(() -> {
                long s = System.nanoTime();
                ValidateResult result = validator.validate(data);
                long e = System.nanoTime();
                latencies.add(e - s);
                if (result.isSuccess()) {
                    okCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // -------------------- 6. 等待完成 --------------------
        latch.await(10, TimeUnit.MINUTES);
        long end = System.nanoTime();
        executor.shutdown();

        // -------------------- 7. 统计结果 --------------------
        long totalTimeMs = (end - start) / 1_000_000;
        double throughput = totalRequests / (totalTimeMs / 1000.0);
        System.out.println("\n========== 压力测试结果 ==========");
        System.out.printf("总请求数: %d\n", totalRequests);
        System.out.printf("总耗时: %d ms\n", totalTimeMs);
        System.out.printf("吞吐量: %.2f ops/s\n", throughput);
        System.out.printf("成功: %d, 失败: %d (失败率: %.2f%%)\n",
                okCount.get(), failCount.get(),
                (failCount.get() * 100.0 / totalRequests));

        // 延迟统计
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        double avgNs = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.printf("平均延迟: %.2f µs\n", avgNs / 1000.0);
        System.out.printf("50%% 延迟: %.2f µs\n", percentile(sortedLatencies, 0.5) / 1000.0);
        System.out.printf("90%% 延迟: %.2f µs\n", percentile(sortedLatencies, 0.9) / 1000.0);
        System.out.printf("99%% 延迟: %.2f µs\n", percentile(sortedLatencies, 0.99) / 1000.0);
        System.out.printf("99.9%% 延迟: %.2f µs\n", percentile(sortedLatencies, 0.999) / 1000.0);
        System.out.println("===================================");
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) (sorted.size() * percentile);
        return sorted.get(index);
    }
}