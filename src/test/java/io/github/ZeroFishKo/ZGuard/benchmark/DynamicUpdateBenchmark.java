package io.github.ZeroFishKo.ZGuard.benchmark;

import io.github.ZeroFishKo.ZGuard.annotation.ValidateWith;
import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.RangeValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.RiskAddrValidator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@Threads(4)
public class DynamicUpdateBenchmark {

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

    @Param({"10", "100", "1000", "5000"})  // 更新间隔（毫秒）
    private int updateIntervalMs;

    private ZGuardValidator validator;
    private ExecutorService updaterExecutor;
    private volatile boolean running = true;

    @Setup(Level.Trial)
    public void setup() {
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"walletAddr", "paymentCode", "chainId"};
            }
        };
        validator = new ZGuardValidator(config, "bench_salt");

        validator.registerValidators(Arrays.asList(
                new RequiredValidator(),
                new Web3WalletValidator(),
                new PaymentCodeValidator(),
                new RangeValidator(),
                new Web3ChainIdValidator(),
                new RiskAddrValidator()
        ));
        validator.bindAnnotations(PaymentRequest.class);

        // 启动后台更新线程
        updaterExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "config-updater");
            t.setDaemon(true);
            return t;
        });

        updaterExecutor.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(updateIntervalMs);
                    int newLength = ThreadLocalRandom.current().nextInt(4, 11);
                    Map<String, Object> params = new HashMap<>();
                    params.put("codeLength", newLength);
                    validator.dynamicUpdateValidatorParams("paymentCode", params);
                    validator.rebuildCombineSteps();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        running = false;
        if (updaterExecutor != null) {
            updaterExecutor.shutdownNow();
            try {
                if (!updaterExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // force shutdown
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Benchmark
    public void validateWithDynamicUpdates(Blackhole bh) {
        Map<String, Object> data = new HashMap<>();
        data.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        data.put("paymentCode", "12345678");
        data.put("chainId", "56");
        data.put("transferAmount", 500.0);
        bh.consume(validator.validate(data));
    }
}