package io.github.ZeroFishKo.ZGuard.benchmark;

import io.github.ZeroFishKo.ZGuard.annotation.ValidateWith;
import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;
import io.github.ZeroFishKo.ZGuard.core.ValidateResult;
import io.github.ZeroFishKo.ZGuard.impl.RequiredValidator;
import io.github.ZeroFishKo.ZGuard.impl.RangeValidator;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3WalletValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.Web3ChainIdValidator;
import io.github.ZeroFishKo.ZGuard.impl.web3.RiskAddrValidator;
import io.github.ZeroFishKo.ZGuard.util.hash.HashStrategy;
import io.github.ZeroFishKo.ZGuard.util.hash.HmacSha256Strategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class BatchValidateBenchmark {

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

    @Param({"10", "100", "1000"})
    private int batchSize;

    @Param({"0.0", "0.2", "0.5"})  // 无效数据比例：0%全部有效，20%无效，50%无效
    private double invalidRatio;

    private ZGuardValidator validator;
    private List<Map<String, Object>> dataList;

    @Setup(Level.Trial)
    public void setup() {
        ValidatorConfig config = new ValidatorConfig() {
            @Override
            public String[] getCoreFields() {
                return new String[]{"walletAddr", "paymentCode", "chainId"};
            }

            @Override
            public boolean isBatchFastFail() {
                return true; // 保持快速失败模式
            }
            @Override
            public HashStrategy getHashStrategy(){
                return new HmacSha256Strategy();
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

        // 生成数据列表，按比例混入无效数据
        dataList = new ArrayList<>(batchSize);
        int invalidCount = (int) (batchSize * invalidRatio);
        int validCount = batchSize - invalidCount;

        // 先生成无效数据（放在前面，以便快速失败尽早触发）
        for (int i = 0; i < invalidCount; i++) {
            dataList.add(TestDataGenerator.mixedData(0.0)); // 生成一个无效数据
        }
        // 再生成有效数据
        for (int i = 0; i < validCount; i++) {
            dataList.add(TestDataGenerator.validData());
        }
        // 注意：无效数据在前，快速失败会在处理到第一个无效数据时停止，不会处理后续有效数据。
        // 如果想模拟随机分布，可以打乱列表。
        // 在数据生成后添加
        Collections.shuffle(dataList, new Random(12345)); // 固定种子保证可重复
    }


    @Benchmark
    public void batchValidate(Blackhole bh) {
        List<ValidateResult> results = validator.batchValidate(dataList);
        bh.consume(results);
    }
}