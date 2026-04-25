package io.github.ZeroFishKo.ZGuard.impl;

import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 通用正则表达式校验器（预编译正则，支持超时保护）。
 * <p>
 * 此校验器检查字符串是否与给定的正则表达式匹配。
 * 正则表达式模式可通过 {@link #setExtParams(Map)} 或 YAML 绑定设置。
 * 为了防止 ReDoS 攻击，可以配置超时；如果校验超过超时时间，则中止并视为失败。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * validatorParams:
 *   regex:
 *     regex: "^[A-Za-z0-9]+$"
 *     timeoutMs: 100
 * </pre>
 *
 * @see ValidatorHandler
 */
public class RegexValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(RegexValidator.class);

    // 共享线程池（首选虚拟线程，否则回退到固定线程池）
    private static final ExecutorService REGEX_EXECUTOR = createExecutor();

    private static ExecutorService createExecutor() {
        try {
            // JDK 21+ 使用虚拟线程
            Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (ExecutorService) method.invoke(null);
        } catch (Exception e) {
            // 回退到固定线程池（守护线程，避免阻塞 JVM 退出）
            return Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r, "regex-worker");
                        t.setDaemon(true);
                        return t;
                    }
            );
        }
    }

    private Pattern regexPattern;
    private long timeoutMs; // 超时毫秒数，0 表示禁用

    /**
     * 校验字符串是否与配置的正则表达式模式匹配。
     * <p>
     * 如果设置了超时且校验时间超过超时，将中止并返回 {@code false}。
     * </p>
     *
     * @param value 要校验的字符串
     * @return 如果字符串匹配模式（且未超时）返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String) || regexPattern == null) {
            return false;
        }
        String s = (String) value;

        if (timeoutMs <= 0) {
            // 无超时，直接执行
            return regexPattern.matcher(s).matches();
        }

        // 启用超时，提交任务到线程池
        Future<Boolean> future = REGEX_EXECUTOR.submit(() -> regexPattern.matcher(s).matches());
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // 尝试中断
            log.warn("正则校验超时 {} ms，输入长度 {}", timeoutMs, s.length());
            return false; // 超时视为校验失败
        } catch (Exception e) {
            log.error("正则校验错误", e);
            return false;
        }
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 包含正则表达式模式或“未配置”的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "格式与正则表达式不匹配：" + (regexPattern != null ? regexPattern.pattern() : "未配置");
    }

    @Override
    public Object[] getMessageArguments() {
        return new Object[]{regexPattern};
    }

    /**
     * 此校验器的优先级（3 = 中高）。
     *
     * @return 优先级值
     */
    @Override
    public int getPriority() {
        return 3;
    }

    /**
     * 此校验器的唯一键。
     *
     * @return "regex"
     */
    @Override
    public String getValidatorKey() {
        return "regex";
    }

    /**
     * 动态更新正则表达式模式和超时。
     * <p>
     * 期望一个可能包含以下键的映射：
     * </p>
     * <ul>
     *   <li>{@code "regex"} – 正则表达式字符串。</li>
     *   <li>{@code "timeoutMs"} – 超时毫秒数（long）。</li>
     * </ul>
     *
     * @param extParams 包含新参数的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        if (extParams == null) return;

        // 设置正则表达式模式
        if (extParams.containsKey("regex")) {
            Object regexObj = extParams.get("regex");
            if (regexObj != null) {
                String regex = regexObj.toString().trim();
                if (!regex.isEmpty()) {
                    this.regexPattern = Pattern.compile(regex);
                }
            } else {
                log.warn("正则校验器配置 'regex' 为 null，忽略");
            }
        }

        // 设置超时（毫秒）
        if (extParams.containsKey("timeoutMs")) {
            Object timeoutObj = extParams.get("timeoutMs");
            if (timeoutObj instanceof Number) {
                this.timeoutMs = ((Number) timeoutObj).longValue();
            } else if (timeoutObj instanceof String) {
                try {
                    this.timeoutMs = Long.parseLong((String) timeoutObj);
                } catch (NumberFormatException e) {
                    log.error("timeoutMs 格式无效", e);
                }
            }
        }
    }
}