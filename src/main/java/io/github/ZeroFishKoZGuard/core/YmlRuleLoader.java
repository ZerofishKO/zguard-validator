package io.github.ZeroFishKoZGuard.core;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.YmlConfigBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * YAML 规则加载器，具有周期性轮询和内容 MD5 比较功能。
 * <p>
 * 此类定期读取 YAML 文件（从 classpath），并比较其 MD5 哈希以检测更改，
 * 确保无论文件时间戳如何都能可靠更新。它使用统一的基于流的读取方法，
 * 支持文件系统和 JAR 打包资源。
 * </p>
 *
 * <p>
 * <b>优化：</b>
 * </p>
 * <ul>
 *   <li>统一的流读取（支持 JAR 资源）。</li>
 *   <li>每次变更检测仅读取一次文件（字节用于重新加载）。</li>
 *   <li>直接在字节数组上计算 MD5，避免不必要的字符串转换。</li>
 *   <li>每次重新加载仅解析一次 YAML（共享 {@code ruleMap}）。</li>
 *   <li>可通过构造函数配置轮询间隔。</li>
 *   <li>优雅关闭，线程清理正确。</li>
 *   <li>更好的异常处理和日志记录。</li>
 * </ul>
 *
 * @author example
 */
public class YmlRuleLoader implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(YmlRuleLoader.class);


    private final ZGuardValidator validator;
    private final YmlConfigBinder configBinder;
    private final String ymlPath;
    private final long pollingIntervalMillis;

    private String lastContentMd5 = "";
    private final ScheduledExecutorService scheduler;

    // 线程本地 MD5 实例，避免重复调用 MessageDigest.getInstance()
    private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(new Supplier<MessageDigest>() {
        @Override
        public MessageDigest get() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5 算法不可用", e);
            }
        }
        });

    /**
     * 构造一个新的 YmlRuleLoader，使用默认轮询间隔（1000 ms）。
     *
     * @param validator    要更新的 ZGuardValidator 实例
     * @param ymlPath      YAML 资源路径（例如 "validator-rules.yml"）
     * @param configBinder 用于加载和注入 YAML 配置的绑定器
     */
    public YmlRuleLoader(ZGuardValidator validator, String ymlPath, YmlConfigBinder configBinder) {
        this(validator, ymlPath, configBinder, 1000);
    }

    /**
     * 构造一个新的 YmlRuleLoader，使用自定义轮询间隔。
     *
     * @param validator             要更新的 ZGuardValidator 实例
     * @param ymlPath               YAML 资源路径
     * @param configBinder          用于加载和注入 YAML 配置的绑定器
     * @param pollingIntervalMillis 轮询间隔（毫秒）
     */
    public YmlRuleLoader(ZGuardValidator validator, String ymlPath,
                         YmlConfigBinder configBinder, long pollingIntervalMillis) {
        this.validator = validator;
        this.ymlPath = ymlPath;
        this.configBinder = configBinder;
        this.pollingIntervalMillis = pollingIntervalMillis;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yml-rule-polling");
            t.setDaemon(true);
            return t;
        });

        // 初始加载(读取yml数据、转字节)


        reloadIfChanged(true);

        // 定期启动checkForChanges
        scheduler.scheduleWithFixedDelay(() -> checkForChanges(),
        pollingIntervalMillis, pollingIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 通过比较 MD5 检查 YAML 文件内容是否已更改。
     * 如果更改，则触发重新加载。
     */
    private void checkForChanges() {
        try {
            byte[] currentBytes = readConfigBytesFromStream();
            if (currentBytes == null) {
                log.error("无法读取 YAML 资源：{}", ymlPath);
                return;
            }
            String currentMd5 = md5(currentBytes);
            if (!currentMd5.equals(lastContentMd5)) {
                log.info("文件内容已更改（MD5: {} -> {}），重新加载...", lastContentMd5, currentMd5);
                reloadFromBytes(currentBytes);
            }
        } catch (Exception e) {
            log.error("检查 {} 更改失败", ymlPath, e);
        }
    }

    /**
     * 使用类路径流将 YAML 资源读取为字节数组。
     *
     * @return 文件内容字节数组，如果资源未找到则返回 {@code null}
     * @throws IOException 如果发生 I/O 错误
     */
    private byte[] readConfigBytesFromStream() throws IOException {
        try (InputStream is = getYmlResourceStream()) {
            if (is == null) {
                return null;
            }
            return readStreamToBytes(is);
        }
    }

    /**
     * 从类路径资源加载 YAML 规则并应用。
     * 此方法在初始加载时调用。
     */
    private void reloadIfChanged(boolean force) {
        try {
            //通过getYmlResourceStream拿去yml数据，在readStreamToBytes转成字节
            byte[] configBytes = readConfigBytesFromStream();
            if (configBytes == null) {
                log.error("YAML 配置文件未找到，路径：{}", ymlPath);
                return;
            }
            if (force) {
                reloadFromBytes(configBytes);
            } else {
                String currentMd5 = md5(configBytes);
                if (!currentMd5.equals(lastContentMd5)) {
                    reloadFromBytes(configBytes);
                }
            }
        } catch (Exception e) {
            log.error("从 {} 加载 YAML 规则失败", ymlPath, e);
        }
    }

    /**
     * 从给定的字节数组（YAML 文件内容）重新加载规则。
     * 此方法避免重新读取文件。
     *
     * @param configBytes YAML 文件内容字节数组
     */
    private void reloadFromBytes(byte[] configBytes) {
        long start = System.currentTimeMillis();
        log.info("从 {} 加载 YAML 规则", ymlPath);

        try {
            // 1. 解析 YAML 一次
            Map<String, Object> ruleMap;


            
            try (InputStream byteStream = new ByteArrayInputStream(configBytes)) {
                Yaml yaml = new Yaml();
                //将字节转换成yml(Map类型)赋值给ruleMap
                ruleMap = yaml.load(byteStream);
            }

            if (ruleMap == null) {
                log.warn("YAML 内容为空：{}", ymlPath);
                return;
            }

            // 2. 将配置加载到 Binder（使用单独的流，但不额外读取文件）
            try (InputStream binderStream = new ByteArrayInputStream(configBytes)) {
                //对globalYmlConfig进行赋值Map类型
                configBinder.loadYml(binderStream);
            }
            //读取yml数据，对globalYmlConfig赋值yml的map类型|修改注册校验器的成员变量值改成yml值
            configBinder.bindAllValidators(validator.getValidatorRegistry());

            // 3. 使用解析的 ruleMap 手动更新非注解校验器
            updateFromRuleMap(ruleMap);

            // 4. 更新 MD5
            this.lastContentMd5 = md5(configBytes);
            log.info("YAML 规则加载成功，路径：{}，耗时 {} ms，MD5：{}",
                    ymlPath, System.currentTimeMillis() - start, lastContentMd5);
        } catch (Exception e) {
            log.error("从 {} 应用 YAML 规则失败", ymlPath, e);
        }
    }

    /**
     * 从解析的规则映射更新校验器、字段绑定和风险列表。
     */
    @SuppressWarnings("unchecked")
    private void updateFromRuleMap(Map<String, Object> ruleMap) {
        //对yml中的validatorParams字段调用validate.setsetExtParams手动改值
        if (ruleMap.containsKey("validatorParams")) {
            Map<String, Object> validatorParams = (Map<String, Object>) ruleMap.get("validatorParams");
            if (validatorParams != null) {
                updateValidatorParams(validatorParams);
            }
        }
        //对yml中的fieldBindRules字段中的校验器字段进行，加载到combineSteps中去。重构combineSteps
        //但是只对注册了的校验器进行增删改查操作，不过可以通过dynamicRegisterValidator注册新校验器
        if (ruleMap.containsKey("fieldBindRules")) {
            Map<String, Object> fieldRules = (Map<String, Object>) ruleMap.get("fieldBindRules");
            if (fieldRules != null) {
                updateFieldBindRules(fieldRules);
            }
        }
        //对风险地址校验器的值去添加yml上的危险地址信息
        if (ruleMap.containsKey("riskLists")) {
            Map<String, Object> riskLists = (Map<String, Object>) ruleMap.get("riskLists");
            if (riskLists != null) {
                updateRiskLists(riskLists);
            }
        }
    }

    // ---------- 辅助方法（逻辑不变，但适配到校验器字段） ----------

    private byte[] readStreamToBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; // 更大的缓冲区提高效率
        int len;
        while ((len = stream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private void updateValidatorParams(Map<String, Object> paramsMap) {
        for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
            String validatorKey = entry.getKey();
            ValidatorHandler validator = this.validator.getValidator(validatorKey);
            if (validator == null) {
                log.warn("校验器未找到：{}", validatorKey);
                continue;
            }
            if (validator.getClass().isAnnotationPresent(YmlConfigBinding.class)) {
                log.debug("校验器 {} 使用注解注入，跳过手动更新", validatorKey);
                continue;
            }
            Map<String, Object> extParams = (Map<String, Object>) entry.getValue();
            this.validator.dynamicUpdateValidatorParams(validatorKey, extParams);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateFieldBindRules(Map<String, Object> fieldRules) {
        for (Map.Entry<String, Object> entry : fieldRules.entrySet()) {
            String fieldName = entry.getKey();
            List<String> validatorKeys = (List<String>) entry.getValue();
            validator.dynamicBindFieldValidators(fieldName, validatorKeys);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateRiskLists(Map<String, Object> riskLists) {
        // 获取 yml 中的风险地址的 map 结构: list: [ ... ]
        Map<String, Object> riskAddrs = (Map<String, Object>) riskLists.get("riskWalletAddrs");
        if (riskAddrs != null) {
            // 获取 list 的风险地址值
            List<String> addrs = (List<String>) riskAddrs.get("list");
            if (addrs != null && !addrs.isEmpty()) {
                // JDK 8 兼容方式创建 Map
                Map<String, Object> params = new HashMap<>();
                params.put("list", addrs);
                validator.dynamicUpdateValidatorParams("riskAddr", params);
            }
        }
    }

    private InputStream getYmlResourceStream() {
        return getClass().getClassLoader().getResourceAsStream(ymlPath);
    }

    /**
     * 计算字符串的 MD5 哈希（保留用于向后兼容，但优先使用字节数组版本）。
     */
    private static String md5(String input) {
        return md5(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 哈希。
     */
    private static String md5(byte[] data) {
        MessageDigest md = MD5_DIGEST.get();
        md.reset();
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (!scheduler.isShutdown()) {
            log.info("关闭 YAML 规则轮询，路径：{}", ymlPath);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}