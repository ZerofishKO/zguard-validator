package io.github.ZeroFishKoZGuard.benchmark;

import io.github.ZeroFishKoZGuard.annotation.ValidateWith;
import io.github.ZeroFishKoZGuard.config.ValidatorConfig;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;
import io.github.ZeroFishKoZGuard.impl.RequiredValidator;
import io.github.ZeroFishKoZGuard.impl.RangeValidator;
import io.github.ZeroFishKoZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKoZGuard.impl.web3.RiskAddrValidator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@Threads(4)
public class ZGuardFullBenchmarkEnhanced {

    // 请求类（与原有基准一致）
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

    // 可配置的参数
    @Param({"1000"})   // 数据池大小
    private int poolSize;

    @Param({"0.8"})    // 有效数据比例
    private double validRatio;

    private ZGuardValidator validator;
    private Map<String, Object>[] dataPool;

    @Setup(Level.Trial)
    public void setup() {

        // 1. 配置校验器（与原基准完全一致）
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

        // 2. 生成随机数据池
        dataPool = new Map[poolSize];
        for (int i = 0; i < poolSize; i++) {
            dataPool[i] = TestDataGenerator.mixedData(validRatio);
        }
    }

    @Benchmark
    public void validateRandomMixed(Blackhole bh) {
        int idx = ThreadLocalRandom.current().nextInt(poolSize);
        bh.consume(validator.validate(dataPool[idx]));
    }

    @Benchmark
    public void validateValidFixed(Blackhole bh) {
        // 保留固定有效数据作为对比基线
        Map<String, Object> fixedValid = new HashMap<>();
        fixedValid.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        fixedValid.put("paymentCode", "12345678");
        fixedValid.put("chainId", "56");
        fixedValid.put("transferAmount", 500.0);
        bh.consume(validator.validate(fixedValid));
    }
}