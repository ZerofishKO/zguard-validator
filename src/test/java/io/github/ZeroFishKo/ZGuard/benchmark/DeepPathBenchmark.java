package io.github.ZeroFishKo.ZGuard.benchmark;

import io.github.ZeroFishKo.ZGuard.util.PathValueExtractor;
import io.github.ZeroFishKo.ZGuard.util.XssCleanUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class DeepPathBenchmark {

    private Map<String, Object> deepMap;
    private String longString;

    @Param({"10", "20", "30"})
    private int depth;

    @Param({"100", "1000", "10000"})
    private int stringLength;

    @Setup
    public void setup() {
        // 构建深层嵌套 Map
        deepMap = new HashMap<>();
        Map<String, Object> current = deepMap;
        for (int i = 1; i < depth; i++) {
            Map<String, Object> next = new HashMap<>();
            current.put("level" + i, next);
            current = next;
        }
        current.put("target", "value");

        // 构造大字符串
        StringBuilder sb = new StringBuilder(stringLength);
        sb.append("0x");
        for (int i = 2; i < stringLength; i++) {
            sb.append('a');
        }
        longString = sb.toString();
    }

    @Benchmark
    public void pathExtraction(Blackhole bh) {
        // 提取深度路径
        Object val = PathValueExtractor.getValue(deepMap, buildPath());
        bh.consume(val);
    }

    @Benchmark
    public void xssCleanLargeString(Blackhole bh) {
        String cleaned = (String) XssCleanUtils.fastClean(longString);
        bh.consume(cleaned);
    }

    private String buildPath() {
        StringBuilder path = new StringBuilder();
        for (int i = 1; i < depth; i++) {
            if (i > 1) path.append('.');
            path.append("level").append(i);
        }
        return path.toString();
    }
}