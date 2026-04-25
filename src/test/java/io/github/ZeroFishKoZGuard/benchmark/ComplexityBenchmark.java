package io.github.ZeroFishKoZGuard.benchmark;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.ValidateWith;
import io.github.ZeroFishKoZGuard.config.ValidatorConfig;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;
import io.github.ZeroFishKoZGuard.impl.RangeValidator;
import io.github.ZeroFishKoZGuard.impl.RequiredValidator;
import io.github.ZeroFishKoZGuard.impl.web3.RiskAddrValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKoZGuard.impl.Payment.PaymentCodeValidator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@Threads(4)
public class ComplexityBenchmark {

    // 简单请求类（2个字段，少量校验器）
    public static class SimpleRequest {
        @ValidateWith({"required", "web3Wallet"})
        public String walletAddr;
        @ValidateWith({"required", "paymentCode"})
        public String paymentCode;
    }

    // 中等复杂度请求类（3个字段，含正则和范围）
    public static class MediumRequest {
        @ValidateWith({"required", "web3Wallet", "riskAddr"})
        public String walletAddr;
        @ValidateWith({"required", "paymentCode"})
        public String paymentCode;
        @ValidateWith({"required", "range"})
        public Double amount;
    }

    // 复杂请求类（4个字段，全部校验器）
    public static class ComplexRequest {
        @ValidateWith({"required", "web3Wallet", "riskAddr"})
        public String walletAddr;
        @ValidateWith({"required", "paymentCode"})
        public String paymentCode;
        @ValidateWith({"required", "range"})
        public Double amount;
        @ValidateWith({"required", "web3ChainId"})
        public String chainId;
    }

    private ZGuardValidator simpleValidator;
    private ZGuardValidator mediumValidator;
    private ZGuardValidator complexValidator;
    private Map<String, Object> baseData;

    @Setup(Level.Trial)
    public void setup() {
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"walletAddr", "paymentCode", "chainId"};
            }
        };

        // 创建三个独立的校验器实例，每个绑定不同的请求类
        simpleValidator = new ZGuardValidator(config, "bench_salt");
        simpleValidator.registerValidators(allValidators());
        simpleValidator.bindAnnotations(SimpleRequest.class);

        mediumValidator = new ZGuardValidator(config, "bench_salt");
        mediumValidator.registerValidators(allValidators());
        mediumValidator.bindAnnotations(MediumRequest.class);

        complexValidator = new ZGuardValidator(config, "bench_salt");
        complexValidator.registerValidators(allValidators());
        complexValidator.bindAnnotations(ComplexRequest.class);

        // 基础数据（所有字段都存在）
        baseData = new HashMap<>();
        baseData.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        baseData.put("paymentCode", "12345678");
        baseData.put("amount", 500.0);
        baseData.put("chainId", "56");
    }

    private java.util.List<ValidatorHandler> allValidators() {
        return Arrays.asList(
                new RequiredValidator(),
                new Web3WalletValidator(),
                new PaymentCodeValidator(),
                new RangeValidator(),
                new Web3ChainIdValidator(),
                new RiskAddrValidator()
        );
    }

    @Benchmark
    public void simple(Blackhole bh) {
        bh.consume(simpleValidator.validate(baseData));
    }

    @Benchmark
    public void medium(Blackhole bh) {
        bh.consume(mediumValidator.validate(baseData));
    }

    @Benchmark
    public void complex(Blackhole bh) {
        bh.consume(complexValidator.validate(baseData));
    }
}