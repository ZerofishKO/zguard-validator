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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 2)
public class ZGuardValidatorBenchmark {

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

    @Setup
    public void setup() {
        // 关闭 ZGuardValidator 的日志输出


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
        validData.put("paymentCode", "123456");
        validData.put("chainId", "56");
        validData.put("transferAmount", 100.0);
    }

    @Benchmark
    public boolean validateSingle() {
        return validator.validate(validData).isSuccess();
    }
}