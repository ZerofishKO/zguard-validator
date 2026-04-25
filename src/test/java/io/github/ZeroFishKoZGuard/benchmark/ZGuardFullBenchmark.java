package io.github.ZeroFishKoZGuard.benchmark;


import io.github.ZeroFishKoZGuard.annotation.ValidateWith;
import io.github.ZeroFishKoZGuard.config.ValidatorConfig;
import io.github.ZeroFishKoZGuard.core.ZGuardValidator;
import io.github.ZeroFishKoZGuard.impl.RangeValidator;
import io.github.ZeroFishKoZGuard.impl.RequiredValidator;
import io.github.ZeroFishKoZGuard.impl.web3.RiskAddrValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKoZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKoZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKoZGuard.util.hash.SimpleHashStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
@Threads(4)
public class ZGuardFullBenchmark {
    private final SimpleHashStrategy simpleHash = new SimpleHashStrategy();  // 新增

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

    public static class ManualBindingRequest {
        public String walletAddr;
        public String paymentCode;
        public Double transferAmount;
        public String chainId;
    }

    private ZGuardValidator validator;
    private ZGuardValidator manualValidator;

    private Map<String, Object> validData;
    private Map<String, Object> invalidData1;
    private Map<String, Object> invalidData2;
    private Map<String, Object> missingCoreFieldData;
    private Map<String, Object> largeStringData;
    private Map<String, Object>[] mixedDataPool;

    @Param({"4"})
    private int threads;

    @Setup(Level.Trial)
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

        manualValidator = new ZGuardValidator(config, "manual_salt");
        manualValidator.registerValidators(Arrays.asList(
                new RequiredValidator(),
                new Web3WalletValidator(),
                new PaymentCodeValidator(),
                new RangeValidator(),
                new Web3ChainIdValidator(),
                new RiskAddrValidator()
        ));
        manualValidator.bindFieldValidators("walletAddr",
                Arrays.asList("required", "web3Wallet", "riskAddr"));
        manualValidator.bindFieldValidators("paymentCode",
                Arrays.asList("required", "paymentCode"));
        manualValidator.bindFieldValidators("transferAmount",
                Arrays.asList("required", "range"));
        manualValidator.bindFieldValidators("chainId",
                Arrays.asList("required", "web3ChainId"));

        validData = new HashMap<>();
        validData.put("walletAddr", "0x1234567890abcdef1234567890abcdef12345678");
        validData.put("paymentCode", "12345678");
        validData.put("chainId", "56");
        validData.put("transferAmount", 500.0);

        invalidData1 = new HashMap<>(validData);
        invalidData1.put("walletAddr", "invalid_addr");

        invalidData2 = new HashMap<>(validData);
        invalidData2.put("chainId", "999");

        missingCoreFieldData = new HashMap<>(validData);
        missingCoreFieldData.remove("paymentCode");

        largeStringData = new HashMap<>(validData);
        largeStringData.put("walletAddr", "0x" + new String(new char[10000]).replace('\0', 'a'));

        mixedDataPool = new Map[100];
        Random rand = new Random(12345);
        for (int i = 0; i < 100; i++) {
            if (i < 80) {
                mixedDataPool[i] = validData;
            } else {
                if (i % 2 == 0) {
                    mixedDataPool[i] = invalidData1;
                } else {
                    mixedDataPool[i] = invalidData2;
                }
            }
        }
    }

    @Benchmark
    public void validateValidData(Blackhole bh) {
        bh.consume(validator.validate(validData));
    }

    @Benchmark
    public void validateMixedData(Blackhole bh) {
        int idx = ThreadLocalRandom.current().nextInt(mixedDataPool.length);
        bh.consume(validator.validate(mixedDataPool[idx]));
    }

    @Benchmark
    public void validateMissingCoreField(Blackhole bh) {
        bh.consume(validator.validate(missingCoreFieldData));
    }

    @Benchmark
    public void validateLargeString(Blackhole bh) {
        bh.consume(validator.validate(largeStringData));
    }

    @Benchmark
    public void validateManualBinding(Blackhole bh) {
        bh.consume(manualValidator.validate(validData));
    }

    @Benchmark
    public void validateWithTamperProof(Blackhole bh) {
        Map<String, Object> data = new HashMap<>(validData);
        data.put("walletAddr_hash", simpleHash.hash((String) data.get("walletAddr"), "bench_salt"));
        data.put("paymentCode_hash", simpleHash.hash((String) data.get("paymentCode"), "bench_salt"));
        bh.consume(validator.validate(data));
    }
}