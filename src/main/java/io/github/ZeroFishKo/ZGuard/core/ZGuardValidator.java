package io.github.ZeroFishKo.ZGuard.core;

import io.github.ZeroFishKo.ZGuard.annotation.XssMode;
import io.github.ZeroFishKo.ZGuard.impl.Payment.PaymentCodeValidator;
import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKo.ZGuard.annotation.RuleReloadListener;
import io.github.ZeroFishKo.ZGuard.annotation.ValidateWith;
import io.github.ZeroFishKo.ZGuard.config.ValidatorConfig;
import io.github.ZeroFishKo.ZGuard.i18n.MessageSource;
import io.github.ZeroFishKo.ZGuard.impl.RegexValidator;
import io.github.ZeroFishKo.ZGuard.pojo.ValidationTrace;
import io.github.ZeroFishKo.ZGuard.util.JdkVersionUtils;
import io.github.ZeroFishKo.ZGuard.util.PathValueExtractor;
import io.github.ZeroFishKo.ZGuard.util.VirtualThreadExecutor;
import io.github.ZeroFishKo.ZGuard.util.XssCleanUtils;
import io.github.ZeroFishKo.ZGuard.util.hash.HashStrategy;
import io.github.ZeroFishKo.ZGuard.util.hash.HmacSha256Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 核心高并发校验框架。
 * <p>
 * 此类是校验操作的主要入口。它管理校验器注册、字段到校验器的绑定，
 * 并提供单条和批量校验能力。专为高吞吐、低延迟设计，支持 JDK 8/17/21，
 * 并可自动适配虚拟线程（当可用时）。
 * </p>
 *
 * <p>主要特点：</p>
 * <ul>
 *   <li>预编译的校验步骤，实现最佳性能</li>
 *   <li>线程安全的注册表和不可变步骤列表</li>
 *   <li>支持核心字段快速失败检查</li>
 *   <li>通过可插拔的哈希策略实现防篡改哈希校验</li>
 *   <li>支持可配置模式的 XSS 清洗</li>
 *   <li>支持从 YAML/配置中心动态更新配置</li>
 *   <li>批量校验：根据 JDK 版本自动使用虚拟线程或线程池</li>
 * </ul>
 *
 * <p><b>防篡改哈希校验说明：</b>框架仅对核心字段（由 {@link ValidatorConfig#getCoreFields()} 定义）
 * 校验其对应的 {@code _hash} 字段。非核心字段的 {@code _hash} 会被忽略。
 * 这确保哈希校验聚焦于关键数据，避免不必要的开销。</p>
 *
 * @see ValidatorHandler
 * @see ValidatorConfig
 * @see ValidateResult
 */
public class ZGuardValidator {
    private static final Logger log = LoggerFactory.getLogger(ZGuardValidator.class);
    private final boolean logXssClean;
    private final MessageSource messageSource;
    private final Locale defaultLocale;

    // 用于防篡改校验的哈希策略
    private final HashStrategy hashStrategy;

    private final long updateDelayMs;  // 新增字段
    //存储校验器的标识符、对象
    private final Map<String, ValidatorHandler> validatorRegistry = new ConcurrentHashMap<>();
    //存储校验器成员变量，成员变量上的注解参数
    private final Map<String, List<String>> fieldCombineMap = new ConcurrentHashMap<>();
    private volatile List<FieldCombineStep> combineSteps = Collections.emptyList();
    private final ValidatorConfig config;
    private final String tamperProofSalt;
    private final Set<String> coreFields = new HashSet<>();
    private final List<RuleReloadListener>

            reloadListeners = new CopyOnWriteArrayList<>();




    // ------------------------------ 内部辅助类/方法 ------------------------------
    private static class FieldCombineStep {
        final String fieldName;
        final List<ValidatorHandler> validators;
        final boolean isCore;

        FieldCombineStep(String fieldName, List<ValidatorHandler> validators, boolean isCore) {
            this.fieldName = fieldName;
            this.validators = validators;
            this.isCore = isCore;
        }
    }


    /**
     * 添加一个监听器，在规则重载操作前后接收通知。
     *
     * @param listener 要添加的监听器（null 将被忽略）
     */
    public void addRuleReloadListener(RuleReloadListener listener) {
        if (listener != null) {
            this.reloadListeners.add(listener);
        }
    }

    /**
     * 使用默认配置构造一个新的 {@code ZGuardValidator}。
     * <p>
     * 此构造函数适用于嵌入式场景，简单的默认配置就足够。它使用 {@link DefaultValidatorConfig}
     * 和一个公共的盐值。
     * </p>
     */
    public ZGuardValidator() {
        this.config = new DefaultValidatorConfig();
        this.tamperProofSalt = "zguard_common_salt";
        this.updateDelayMs = this.config.getUpdateDelayMs();
        this.logXssClean = this.config.isLogXssClean();
        this.hashStrategy = this.config.getHashStrategy();
        this.messageSource = null;
        this.defaultLocale = Locale.getDefault();
        initCoreFields();
    }

    public ZGuardValidator(ValidatorConfig config, String tamperProofSalt, MessageSource messageSource) {
        this.config = config != null ? config : new DefaultValidatorConfig();
        this.tamperProofSalt = tamperProofSalt != null ? tamperProofSalt : "default_tamper_salt";
        this.updateDelayMs = this.config.getUpdateDelayMs();
        this.logXssClean = this.config.isLogXssClean();
        this.hashStrategy = this.config.getHashStrategy();
        this.messageSource = messageSource;
        this.defaultLocale = Locale.getDefault();
        initCoreFields();
    }

    /**
     * 使用自定义配置和盐值构造一个新的 {@code ZGuardValidator}。
     * <p>
     * 此构造函数专为高并发或领域特定场景设计。
     * 如果提供的配置或盐值为 null，则回退到默认值，保证空安全。
     * </p>
     *
     * @param config          校验器配置（可能为 null，将使用默认值）
     * @param tamperProofSalt 用于防篡改哈希计算的盐值（可能为 null，将使用默认值）
     */

//    this.config = Objects.requireNonNullElse(config, new DefaultValidatorConfig());
//    objects.requireNonNullElse的意思:config为null就返回Default默认值，否则相反
    /**
     *
     * config初始化分配:updateDelayMs,logXssClean,hashStrategy
     * initCoreFields：config.getCoreFields()传给内部coreFields
     *
     */

    public ZGuardValidator(ValidatorConfig config, String tamperProofSalt) {
        this.config = config != null ? config : new DefaultValidatorConfig();
        this.tamperProofSalt = tamperProofSalt != null ? tamperProofSalt : "default_tamper_salt";
        this.updateDelayMs = this.config.getUpdateDelayMs();
        this.logXssClean = this.config.isLogXssClean();
        this.hashStrategy = this.config.getHashStrategy();
        this.messageSource = null;
        this.defaultLocale = Locale.getDefault();
        initCoreFields();
    }

    /**
     * 初始化核心字段（固定，无空指针风险）
     */
    private void initCoreFields() {
        String[] coreFieldArr = config.getCoreFields();
        if (coreFieldArr != null && coreFieldArr.length > 0) {
            coreFields.addAll(Arrays.asList(coreFieldArr));
        }
    }


    private String getLocalizedErrorMessage(ValidatorHandler validator, Locale locale) {
        if (messageSource != null) {
            return messageSource.getMessage(validator.getMessageKey(), validator.getMessageArguments(), locale);
        }
        return validator.getErrorMessage(locale);
    }

    // ------------------------------ 注册/绑定方法（预排序 + 不可变集合） ------------------------------

    /**
     * 向框架注册单个校验器。
     * <p>
     * 校验器的键必须唯一。注册后，校验步骤会重建以包含任何新的绑定。
     * </p>
     *
     * @param validator 要注册的校验器（不能为 null，且其键不能为 null）
     * @throws IllegalArgumentException 如果校验器或其键为 null
     */
    public void registerValidator(ValidatorHandler validator) {
        if (validator == null || validator.getValidatorKey() == null) {
            throw new IllegalArgumentException("校验器或校验器键不能为空");
        }
        validatorRegistry.put(validator.getValidatorKey(), validator);

        // 为 RegexValidator 设置默认超时
        if (validator instanceof RegexValidator) {
            Map<String, Object> params = new HashMap<>();
            params.put("timeoutMs", config.getRegexTimeoutMs());
            validator.setExtParams(params);
        }

        rebuildCombineSteps();
        log.info("校验器注册成功：{}，注册表大小：{}", validator.getValidatorKey(), validatorRegistry.size());
    }

    /**
     * 一次性注册多个校验器。
     * <p>
     * 这是一个批量注册的便捷方法。注册后，校验步骤会重建一次以包含所有新绑定。
     * </p>
     *
     * @param validators 要注册的校验器集合（可以为空或 null）
     */
    public void registerValidators(Collection<ValidatorHandler> validators) {
        if (validators == null || validators.isEmpty()) {
            return;
        }
        for (ValidatorHandler validator : validators) {
            if (validator != null && validator.getValidatorKey() != null) {
                validatorRegistry.put(validator.getValidatorKey(), validator);

                // 为 RegexValidator 设置默认超时
                if (validator instanceof RegexValidator) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("timeoutMs", config.getRegexTimeoutMs());
                    validator.setExtParams(params);
                }
            }
        }
        rebuildCombineSteps();
        log.info("批量注册了 {} 个校验器，注册表大小：{}", validators.size(), validatorRegistry.size());
    }

    /**
     * 使用给定类上的注解将校验器键绑定到字段。
     * <p>
     * 对于每个类，处理所有带有 {@link ValidateWith} 注解的字段，
     * 并将指定的校验器键绑定到该字段名。
     * </p>
     *
     * @param classes 要扫描 {@code @ValidateWith} 注解的类
     */
    public void bindAnnotations(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            for (Field field : clazz.getDeclaredFields()) {
                ValidateWith annotation = field.getAnnotation(ValidateWith.class);
                if (annotation != null) {
                    String fieldName = field.getName();
                    List<String> validatorKeys = Arrays.asList(annotation.value());
                    bindFieldValidators(fieldName, validatorKeys);
                    log.info("字段 '{}' 绑定校验器：{}", fieldName, validatorKeys);
                }
            }
        }
    }

    /**
     * 将校验器键列表绑定到特定字段。
     * <p>
     * 校验器键在绑定时按优先级排序，以避免运行时排序。
     * 字段存储的是不可变的校验器键列表。
     * 绑定后，校验步骤会重建。
     * </p>
     *
     * @param fieldName     字段名称
     * @param validatorKeys 要绑定的校验器键列表（不能为空）
     * @throws IllegalArgumentException 如果 fieldName 或 validatorKeys 为 null 或空
     */
    public void bindFieldValidators(String fieldName, List<String> validatorKeys) {
        if (fieldName == null || validatorKeys == null || validatorKeys.isEmpty()) {
            throw new IllegalArgumentException("字段名/校验器键不能为空");
        }
        // 在绑定时按优先级排序，避免运行时排序
        List<String> sortedKeys = validatorKeys.stream()
                .map(key -> new AbstractMap.SimpleEntry<>(key, validatorRegistry.get(key)))
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getPriority()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 直接创建不可变列表（JDK 8+ 兼容）
        List<String> immutableKeys = Collections.unmodifiableList(new ArrayList<>(sortedKeys));
        // 或者更简洁：因为 sortedKeys 已经是新建的 ArrayList，可以直接用
        // List<String> immutableKeys = Collections.unmodifiableList(sortedKeys);

        fieldCombineMap.put(fieldName, immutableKeys);
        rebuildCombineSteps();
    }

    // ------------------------------ 预编译组合步骤（消除冗余排序） ------------------------------

    /**
     * 重建预编译的校验步骤。
     * <p>
     * 此方法根据当前的字段-校验器绑定和校验器注册表重新构建 {@link FieldCombineStep} 对象列表。
     * 它确保更新的校验器参数（例如超时、代码长度）反映在执行步骤中。
     * </p>
     * <p>
     * 此方法在每次注册或绑定更改后自动调用，但也可以手动调用。
     * </p>
     */
    public void rebuildCombineSteps() {
        reloadListeners.forEach(RuleReloadListener::onBeforeReload);
        List<FieldCombineStep> steps = new ArrayList<>();


        for (Map.Entry<String, List<String>> entry : fieldCombineMap.entrySet()) {
            String fieldName = entry.getKey();
            List<String> validatorKeys = entry.getValue();
            List<ValidatorHandler> validators = new ArrayList<>();

            // 严格按照绑定顺序添加校验器，不再额外排序
            for (String key : validatorKeys) {
                ValidatorHandler handler = validatorRegistry.get(key);
                if (handler != null) {
                    validators.add(handler);
                    log.debug("rebuildCombineSteps: field={}, added validator key={}, instance hash={}",
                            fieldName, key, handler.hashCode());
                }
            }

            if (!validators.isEmpty()) {
                steps.add(new FieldCombineStep(
                        fieldName,
                        validators,
                        coreFields.contains(fieldName)
                ));
            }
        }

        Map<String, Integer> priorityMap = new HashMap<>();
        int index = 0;
        for (String field : coreFields) {
            priorityMap.put(field, index++);
        }
        steps.sort((s1, s2) -> {
            int p1 = priorityMap.getOrDefault(s1.fieldName, Integer.MAX_VALUE);
            int p2 = priorityMap.getOrDefault(s2.fieldName, Integer.MAX_VALUE);
            return Integer.compare(p1, p2);
        });

        this.combineSteps = Collections.unmodifiableList(steps);
        log.info("rebuildCombineSteps 完成：步骤数={}，校验器总数={}",
                steps.size(), steps.stream().mapToInt(s -> s.validators.size()).sum());
        reloadListeners.forEach(RuleReloadListener::onAfterReload);
    }

    // ------------------------------ 单条校验（安全增强 + 高响应） ------------------------------

    /**
     * 校验单个数据对象。
     * <p>
     * 输入是一个字段名到值的映射。校验过程包括：
     * </p>
     * <ul>
     *   <li>对缺失的核心字段进行快速失败检查</li>
     *   <li>如果存在 {@code _hash} 字段，进行可选的防篡改哈希校验</li>
     *   <li>使用配置的 {@link XssMode} 对字符串值进行 XSS 清洗</li>
     *   <li>按优先级顺序执行绑定到每个字段的所有校验器</li>
     * </ul>
     *
     * <p><b>防篡改哈希校验：</b>如果核心字段（根据 {@link ValidatorConfig#getCoreFields()}）在数据映射中有对应的
     * {@code _hash} 字段，框架使用配置的 {@link HashStrategy} 计算期望的哈希值，
     * 并与提供的值进行比较。如果不匹配，校验立即失败，错误信息为"Data tampered"。
     * 非核心字段不进行哈希校验。</p>
     *
     * @param data 要校验的数据（可能为 null 或空）
     * @return 表示成功或失败的 {@link ValidateResult}
     */
    public ValidateResult validate(Map<String, Object> data) {
        return validate(data, defaultLocale);
    }

    public ValidateResult validate(Map<String, Object> data, Locale locale) {
        if (data == null || data.isEmpty()) {
            return ValidateResult.fail("data", getLocalizedMessage("data.empty", locale));
        }
        // 核心字段空检查（外层快速失败）
        for (String coreField : coreFields) {
            if (!data.containsKey(coreField) || data.get(coreField) == null) {
                return ValidateResult.fail(coreField, getLocalizedMessage("core.missing", locale));
            }
        }
        // 执行组合校验
        for (FieldCombineStep step : combineSteps) {
            Object value = PathValueExtractor.getValue(data, step.fieldName);
            if (value == null) {
                if (step.isCore) {
                    return ValidateResult.fail(step.fieldName, getLocalizedMessage("core.missing", locale));
                }
                continue;
            }

            // 关键日志：打印当前校验器实例和 codeLength（确保使用最新实例）
            for (ValidatorHandler validator : step.validators) {
                if (validator instanceof PaymentCodeValidator) {
                    int currentLength = ((PaymentCodeValidator) validator).getCodeLength();
                    String valueStr = (value instanceof String) ? (String) value : String.valueOf(value);
                    log.info("=== 校验中：field={}, instance hash={}, current codeLength={}, value={}, value length={}",
                            step.fieldName, validator.hashCode(), currentLength, valueStr, valueStr.length());
                }
            }

            // 防篡改哈希校验（使用可配置的哈希策略）
            if (step.isCore && data.containsKey(step.fieldName + "_hash")) {
                String valueStr;
                if (value instanceof Number || value instanceof Boolean) {
                    valueStr = value.toString();
                } else if (value instanceof String) {
                    valueStr = (String) value;
                } else {
                    return ValidateResult.fail(step.fieldName, getLocalizedMessage("tamper.unsupported", locale));
                }

                String expectedHash = hashStrategy.hash(valueStr, tamperProofSalt);
                Object actualHashObj = data.get(step.fieldName + "_hash");

                boolean hashMatch = false;
                if (actualHashObj instanceof String) {
                    hashMatch = expectedHash.equals(((String) actualHashObj).trim());
                } else if (actualHashObj instanceof Integer) {
                    // 向后兼容：将 int 转换为字符串
                    hashMatch = expectedHash.equals(String.valueOf(actualHashObj));
                }

                if (!hashMatch) {
                    return ValidateResult.fail(step.fieldName, getLocalizedMessage("data.tampered", locale));
                }
            }

            // XSS 清洗（增强版）
            Object cleanValue = XssCleanUtils.fastClean(value, config.getXssMode());
            if (logXssClean) {
                if (cleanValue instanceof String) {
                    log.info("=== XSS 清洗后的值：{}，长度={}", cleanValue, ((String) cleanValue).length());
                } else {
                    log.info("=== XSS 清洗后的值：{} (类型：{})", cleanValue,
                            cleanValue != null ? cleanValue.getClass().getSimpleName() : "null");
                }
            }

            // 组合校验快速失败
            for (ValidatorHandler validator : step.validators) {
                boolean isValid = validator.validate(cleanValue);
                log.info("=== 校验结果：validator={}, passed={}, message={}",
                        validator.getValidatorKey(), isValid, validator.getErrorMessage());
                if (!isValid) {
                    String errorMsg = getLocalizedErrorMessage(validator, locale);
                    return ValidateResult.fail(step.fieldName, errorMsg);
                }
            }
        }
        return ValidateResult.success();
    }


    private String getLocalizedMessage(String key, Locale locale, Object... args) {
        if (messageSource != null) {
            return messageSource.getMessage(key, args, locale);
        }
        // 未提供 MessageSource 时使用的后备硬编码消息
        switch (key) {
            case "data.empty":
                return "数据不能为空";
            case "core.missing":
                return "核心字段不能为空";
            case "data.tampered":
                return "数据被篡改";
            case "tamper.unsupported":
                return "核心字段类型不支持防篡改校验";
            default:
                return key;
        }
    }

    // ------------------------------ 批量校验（动态线程池适配 + 虚拟线程） ------------------------------

    /**
     * 批量校验数据对象列表。
     * <p>
     * 根据 JDK 版本，此方法自动使用虚拟线程（JDK 21+）或传统线程池（JDK 8/17）并发执行校验。
     * 批处理大小可通过 {@link #setBatchSize(int)} 配置。
     * </p>
     *
     * @param dataList 要校验的数据映射列表（可能为 null 或空）
     * @return {@link ValidateResult} 对象列表，顺序与输入相同，
     * 包含所有项的结果（包括因快速失败而跳过的项）
     */
    public List<ValidateResult> batchValidate(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }
        if (JdkVersionUtils.isJdk21OrHigher()) {
            return batchValidateWithVirtualThread(dataList);
        } else {
            return batchValidateWithThreadPool(dataList);
        }
    }

    private volatile int batchSize = 200;

    /**
     * 设置批量校验的批大小。
     * <p>
     * 这控制每批提交给执行器的校验任务数量。
     * 较大的批大小可能提高吞吐量，但也增加内存使用。
     * </p>
     *
     * @param batchSize 批大小（必须为正数）
     */
    public void setBatchSize(int batchSize) {
        if (batchSize > 0) {
            this.batchSize = batchSize;
        }
    }

    /**
     * JDK 21+ 虚拟线程实现（高吞吐，完整统计）
     */
    private List<ValidateResult> batchValidateWithVirtualThread(List<Map<String, Object>> dataList) {
        log.info("=== 虚拟线程批量校验已触发（JDK 21+） ===");
        int batchSize = this.batchSize;
        List<ValidateResult> allResults = new ArrayList<>(dataList.size());
        ExecutorService executor = VirtualThreadExecutor.createExecutor();
        try {
            for (int i = 0; i < dataList.size(); i += batchSize) {
                // 如果当前线程被中断（由之前批次的失败触发），停止提交剩余批次
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("批量校验被中断，停止剩余任务");
                    break;
                }

                int end = Math.min(i + batchSize, dataList.size());
                List<Map<String, Object>> subList = dataList.subList(i, end);
                List<Future<ValidateResult>> futures = new ArrayList<>(subList.size());

                // 提交当前批次的所有任务
                for (Map<String, Object> data : subList) {
                    futures.add(executor.submit(() -> validate(data)));
                }

                // 收集当前批次的结果；如果发生失败且快速失败启用，立即中断主线程
                for (Future<ValidateResult> future : futures) {
                    try {
                        ValidateResult result = future.get(10, TimeUnit.SECONDS);
                        allResults.add(result);
                        if (!result.isSuccess() && config.isBatchFastFail()) {
                            log.info("检测到校验失败，快速失败已启用，终止后续批次");
                            Thread.currentThread().interrupt(); // 设置中断标志
                            // 尝试取消当前批次中未完成的任务
                            for (Future<ValidateResult> f : futures) {
                                if (!f.isDone()) {
                                    f.cancel(true);
                                }
                            }
                            break; // 退出当前批次的结果收集循环
                        }
                    } catch (Exception e) {
                        allResults.add(ValidateResult.fail("batch_error", "校验异常：" + e.getMessage()));
                    }
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 由于中断导致的缺失结果填充
        if (allResults.size() < dataList.size()) {
            int remaining = dataList.size() - allResults.size();
            for (int j = 0; j < remaining; j++) {
                allResults.add(ValidateResult.fail("batch_fast_fail", "由于之前的失败，校验被终止"));
            }
        }
        return allResults;
    }

    /**
     * JDK 8/17 线程池实现（动态适配 + 无资源泄漏 + 无缺失结果）
     */
    private List<ValidateResult> batchValidateWithThreadPool(List<Map<String, Object>> dataList) {
        int size = dataList.size();
        int batchSize = this.batchSize;
        List<ValidateResult> allResults = new ArrayList<>(size);
        ExecutorService executor = createThreadPoolExecutor(dataList); // 提取线程池创建逻辑
        try {
            for (int i = 0; i < size; i += batchSize) {
                // 如果线程池已关闭（由之前批次的失败触发），停止提交
                if (executor.isShutdown()) {
                    break;
                }

                int end = Math.min(i + batchSize, size);
                List<Map<String, Object>> subList = dataList.subList(i, end);
                List<Future<ValidateResult>> futures = new ArrayList<>(subList.size());

                for (Map<String, Object> data : subList) {
                    futures.add(executor.submit(() -> validate(data)));
                }

                for (Future<ValidateResult> future : futures) {
                    try {
                        ValidateResult result = future.get(10, TimeUnit.SECONDS);
                        allResults.add(result);
                        if (!result.isSuccess() && config.isBatchFastFail()) {
                            log.info("检测到校验失败，快速失败已启用，立即关闭线程池");
                            executor.shutdownNow(); // 立即关闭，中断正在运行的任务
                            // 注意：shutdownNow 后，剩余 future 可能无法访问，跳出循环
                            break;
                        }
                    } catch (Exception e) {
                        allResults.add(ValidateResult.fail("batch_error", "校验异常：" + e.getMessage()));
                    }
                }
                // 如果线程池已关闭，提前退出外层循环
                if (executor.isShutdown()) {
                    break;
                }
            }
        } finally {
            // 确保最终关闭
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 由于中断导致的缺失结果填充
        if (allResults.size() < size) {
            int remaining = size - allResults.size();
            for (int j = 0; j < remaining; j++) {
                allResults.add(ValidateResult.fail("batch_fast_fail", "由于之前的失败，校验被终止"));
            }
        }
        return allResults;
    }

    private ExecutorService createThreadPoolExecutor(List<Map<String, Object>> dataList) {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Math.min(corePoolSize * 2, 32);
        int queueCapacity = Math.min(dataList.size() * 2, 10000);
        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "batch-validate-" + ThreadLocalRandom.current().nextInt(1000));
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // ------------------------------ 动态更新方法（减少冗余复制） ------------------------------

    /**
     * 动态注册一个新的校验器。
     * <p>
     * 此方法类似于 {@link #registerValidator(ValidatorHandler)}，但设计用于运行时更新，
     * 例如来自配置中心的更新。注册后重建校验步骤。
     * </p>
     *
     * @param validator 要注册的校验器
     * @throws IllegalArgumentException 如果校验器或其键为 null
     */
    public void dynamicRegisterValidator(ValidatorHandler validator) {
        if (validator == null || validator.getValidatorKey() == null) {
            throw new IllegalArgumentException("校验器或键不能为空");
        }
        validatorRegistry.put(validator.getValidatorKey(), validator);
        rebuildCombineSteps();
        log.info("动态注册成功：{}", validator.getValidatorKey());
    }

    /**
     * 动态更新现有校验器的参数。
     * <p>
     * 此方法用于将配置更改（例如来自 YAML 更新）应用到校验器。
     * 使用提供的参数映射调用校验器的 {@link ValidatorHandler#setExtParams(Map)} 方法。
     * </p>
     *
     * @param validatorKey 要更新的校验器的键
     * @param extParams    新参数（不能为 null）
     * @throws IllegalArgumentException 如果 validatorKey 或 extParams 为 null
     * @throws RuntimeException         如果未找到给定键的校验器
     */
    public void dynamicUpdateValidatorParams(String validatorKey, Map<String, Object> extParams) {
        if (validatorKey == null || extParams == null) {
            throw new IllegalArgumentException("校验器键/参数不能为空");
        }
        //获取校验器实例
        ValidatorHandler validator = this.validatorRegistry.get(validatorKey);
        if (validator == null) {
            throw new RuntimeException("未找到校验器：" + validatorKey);
        }
        //把yml的参数传入校验器的setExtParams，手动实现动态规则
        validator.setExtParams(extParams);
        log.info("动态参数更新成功：{}，参数键：{}", validatorKey, extParams.keySet());
    }


    /**
     * 具有安全默认值的默认配置。
     */
    private static class DefaultValidatorConfig implements ValidatorConfig {
        @Override
        public boolean isBatchFastFail() {
            return true;
        }

        @Override
        public boolean isCombineFastFail() {
            return true;
        }

        @Override
        public HashStrategy getHashStrategy() {
            return new HmacSha256Strategy(); // 更安全
        }
    }

    // ------------------------------ 公开方法 ------------------------------

    /**
     * 返回校验器注册表的不可修改视图。
     *
     * @return 从校验器键到 {@link ValidatorHandler} 实例的映射
     */
    public Map<String, ValidatorHandler> getValidatorRegistry() {
        return Collections.unmodifiableMap(this.validatorRegistry);
    }

    /**
     * 通过键检索校验器。
     *
     * @param validatorKey 校验器键
     * @return 校验器，如果未找到则返回 {@code null}
     */
    public ValidatorHandler getValidator(String validatorKey) {
        return validatorRegistry.get(validatorKey);
    }

    /**
     * 动态更新整个配置并重建校验步骤。
     * <p>
     * 此方法通常在从配置中心（例如 Nacos、Apollo）接收到新的 YAML 配置时调用。
     * 它执行以下步骤：
     * <ol>
     *   <li>通过绑定器更新全局 YAML 配置</li>
     *   <li>提取并应用字段绑定规则</li>
     *   <li>提取并更新校验器参数</li>
     *   <li>等待一个短暂的延迟（由 {@link ValidatorConfig#getUpdateDelayMs()} 配置）
     *       以确保参数注入完成</li>
     *   <li>重建组合校验步骤</li>
     * </ol>
     *
     * @param newYmlStream 包含新 YAML 配置的输入流
     * @param configBinder 用于解析和应用配置的绑定器
     */
    public void updateConfigAndRebuild(InputStream newYmlStream, YmlConfigBinder configBinder) {
        synchronized (this.validatorRegistry) {
            // 1. 清空字段绑定缓存（占位，未实现）
            // 2. 动态加载 YML 并绑定参数
            configBinder.dynamicUpdate(newYmlStream, this.validatorRegistry);

            // 3. 提取并更新校验器参数
            Map<String, Object> globalConfig = configBinder.getGlobalYmlConfig();
            if (globalConfig.containsKey("fieldBindRules")) {
                Map<String, Object> fieldRules = (Map<String, Object>) globalConfig.get("fieldBindRules");
                if (fieldRules != null && !fieldRules.isEmpty()) {
                    for (Map.Entry<String, Object> entry : fieldRules.entrySet()) {
                        String fieldName = entry.getKey();
                        List<String> validatorKeys = (List<String>) entry.getValue();
                        if (validatorKeys != null && !validatorKeys.isEmpty()) {
                            this.dynamicBindFieldValidators(fieldName, validatorKeys);
                        }
                    }
                }
            }

            if (globalConfig.containsKey("validatorParams")) {
                Map<String, Object> validatorParams = (Map<String, Object>) globalConfig.get("validatorParams");
                if (validatorParams != null && !validatorParams.isEmpty()) {
                    for (Map.Entry<String, Object> entry : validatorParams.entrySet()) {
                        String validatorKey = entry.getKey();
                        Map<String, Object> extParams = (Map<String, Object>) entry.getValue();
                        if (extParams != null) {
                            this.dynamicUpdateValidatorParams(validatorKey, extParams);
                        }
                    }
                }
            }

            // 4. 关键修复：添加 100ms 延迟以确保参数注入完成（解决并发注入延迟问题）
            try {
                Thread.sleep(this.updateDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 5. 重建组合步骤
            this.rebuildCombineSteps();
        }
    }

    // ------------------------------ 新增动态绑定方法 ------------------------------

    /**
     * 动态更新字段-校验器绑定规则（用于配置中心动态更新）
     * 与 bindFieldValidators 逻辑一致，适配动态更新场景。
     *
     * @param fieldName     字段名称
     * @param validatorKeys 要绑定的校验器键列表（不能为空）
     * @throws IllegalArgumentException 如果 fieldName 或 validatorKeys 为 null 或空
     */
    public void dynamicBindFieldValidators(String fieldName, List<String> validatorKeys) {
        if (fieldName == null || validatorKeys == null || validatorKeys.isEmpty()) {
            throw new IllegalArgumentException("字段名/校验器键不能为空");
        }

        // 使用 Collectors.toList() 收集（JDK 8 兼容）
        List<String> sortedKeys = validatorKeys.stream()
                .map(key -> new AbstractMap.SimpleEntry<>(key, validatorRegistry.get(key)))
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getPriority()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());  // 统一使用 Collectors.toList()

        // 统一使用 Collections.unmodifiableList 创建不可变列表（JDK 8 兼容）
        List<String> immutableKeys = Collections.unmodifiableList(new ArrayList<>(sortedKeys));

        fieldCombineMap.put(fieldName, immutableKeys);
        rebuildCombineSteps();
        log.info("动态字段绑定成功：{} → {}", fieldName, sortedKeys);
    }

    /**
     * 校验数据并返回每个校验步骤的详细跟踪。
     * <p>
     * 此方法对调试或基于 AI 的规则分析很有用。它执行与 {@link #validate(Map)} 相同的校验，
     * 但为每个校验器执行收集跟踪信息，即使校验通过也会收集。
     * </p>
     *
     * @param data 要校验的数据（可能为 null）
     * @return {@link ValidationTrace} 对象列表，每个校验器执行一个
     */
    public List<ValidationTrace> validateWithTrace(Map<String, Object> data) {
        List<ValidationTrace> traces = new ArrayList<>();
        if (data == null) {
            traces.add(new ValidationTrace("data", "required", false, "输入数据不能为 null"));
            return traces;
        }
        // 核心字段缺失检查
        for (String coreField : config.getCoreFields()) {
            if (!data.containsKey(coreField) || data.get(coreField) == null) {
                traces.add(new ValidationTrace(coreField, "required", false, "核心字段缺失"));
            }
        }
        // 字段-校验器组合跟踪
        for (FieldCombineStep step : combineSteps) {
            Object value = PathValueExtractor.getValue(data, step.fieldName);
            for (ValidatorHandler validator : step.validators) {
                boolean passed = validator.validate(value);
                String message = passed ? "" : validator.getErrorMessage();
                traces.add(new ValidationTrace(
                        step.fieldName,
                        validator.getValidatorKey(),
                        passed,
                        message
                ));
            }
        }
        return traces;
    }
}