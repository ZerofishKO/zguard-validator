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
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@Threads(20)
public class ZGuardConcurrentBenchmark {

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

    private ZGuardValidator validator;
    private Map<String, Object> validData;

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

        validData = new HashMap<>();
        validData.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        validData.put("paymentCode", "12345678");
        validData.put("chainId", "56");
        validData.put("transferAmount", 500.0);
    }

    @Benchmark
    public void validateConcurrent(Blackhole blackhole) {
        blackhole.consume(validator.validate(validData));
    }
}