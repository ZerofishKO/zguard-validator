package io.github.ZeroFishKo.ZGuard.core;

import io.github.ZeroFishKo.ZGuard.annotation.YmlConfigBinding;
import io.github.ZeroFishKo.ZGuard.Interface.ValidatorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级配置绑定器，支持多 JDK 环境和优化反射缓存。
 * <p>
 * 此类负责：
 * </p>
 * <ul>
 *   <li>将 YAML 配置文件加载到结构化 Map 中。</li>
 *   <li>通过反射将配置值绑定到 {@link ValidatorHandler} 实例。</li>
 *   <li>支持运行时动态配置更新。</li>
 *   <li>自动对敏感数据（如 secret、salt）进行日志脱敏。</li>
 * </ul>
 * <p>
 * <b>线程安全性：</b>此类在并发调用 {@link #loadYml(InputStream)} 或
 * {@link #dynamicUpdate(InputStream, Map)} 时不是线程安全的，但初始化后的读操作是安全的。
 * </p>
 *
 * @see YmlConfigBinding
 * @see ValidatorHandler
 */
public class YmlConfigBinder {
    private static final Logger log = LoggerFactory.getLogger(YmlConfigBinder.class);
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private Map<String, Object> globalYmlConfig;

    /**
     * 从提供的输入流加载 YAML 配置。
     * <p>
     * 加载的配置存储在内部，用于后续的绑定操作。
     * 如果流包含空内容，则初始化为空 Map。
     * </p>
     *
     * @param ymlStream 包含 YAML 数据的输入流（不能为 null）
     * @throws RuntimeException 如果 YAML 内容无效或无法解析
     */
    public void loadYml(InputStream ymlStream) {
        // 1. 先将输入流内容全部读取到字节数组（JDK 8 兼容）
        byte[] yamlBytes;
        try {
            yamlBytes = toByteArray(ymlStream);
        } catch (IOException e) {
            throw new RuntimeException("读取 YAML 输入流失败", e);
        }

        // 2. 从字节数组创建新流用于 YAML 解析
        try (InputStream parsingStream = new ByteArrayInputStream(yamlBytes)) {
            Yaml yaml = new Yaml();
            this.globalYmlConfig = yaml.load(parsingStream);
            if (this.globalYmlConfig == null) {
                this.globalYmlConfig = new HashMap<>();
                log.warn("YAML 配置内容为空，初始化空配置");
            } else {
                log.info("YAML 配置加载成功，配置节点数：{}", globalYmlConfig.size());
                log.debug("YAML 配置详情（已脱敏）：{}", desensitizeConfig(globalYmlConfig));
            }
        } catch (Exception e) {
            // 3. 解析失败时，使用已保存的字节数组输出原始内容（用于调试）
            String yamlContent = new String(yamlBytes, StandardCharsets.UTF_8);
            log.error("YAML 配置加载失败。配置内容（已脱敏）：\n{}",
                    desensitizeYmlContent(yamlContent.isEmpty() ? "[不可读]" : yamlContent), e);
            throw new RuntimeException("YAML 配置加载失败", e);
        }
    }

    // 辅助方法：将 InputStream 完整读取为字节数组
    private static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * 将配置值绑定到所有已注册的校验器。
     * <p>
     * 遍历提供的校验器注册表，对每个带有 {@link YmlConfigBinding} 注解的校验器
     * 调用 {@link #bindConfigToValidator(ValidatorHandler)}。
     * </p>
     *
     * @param validatorRegistry 校验器键到 {@link ValidatorHandler} 实例的映射
     */
    public void bindAllValidators(Map<String, ValidatorHandler> validatorRegistry) {
        for (ValidatorHandler validator : validatorRegistry.values()) {
            bindConfigToValidator(validator);
        }
    }

    /**
     * 根据 {@link YmlConfigBinding} 注解将配置值绑定到单个校验器实例。
     * <p>
     * 从注解中提取配置路径，从全局配置中获取对应的子 Map，
     * 然后通过反射将匹配的字段注入到校验器中。
     * </p>
     *
     * @param validator 要配置的校验器实例
     */
    private void bindConfigToValidator(ValidatorHandler validator) {
        Class<?> clazz = validator.getClass();
        /**
         * clazz.isAnnotationPresent(YmlConfigBinding.class)
        查看该类上是否有YmlConfigBinding.class注解
         */
        //该类上有注解就向下执行，没有换一个校验器来判断
        if (!clazz.isAnnotationPresent(YmlConfigBinding.class)) {
            return;
        }
        //拿到校验器上的注解变量
        YmlConfigBinding annotation = clazz.getAnnotation(YmlConfigBinding.class);
        //拿到注解的参数值
        String configPath = annotation.configPath();
        //对configPath分割，获取key，value字段
        Map<String, Object> validatorConfig = getConfigByPath(configPath);

        if (validatorConfig == null || validatorConfig.isEmpty()) {
            log.warn("校验器 [{}] 未找到有效配置，路径：{}", validator.getValidatorKey(), configPath);
            return;
        }

        log.debug("校验器 [{}] 开始配置注入，配置内容（已脱敏）：{}",
                validator.getValidatorKey(), desensitizeConfig(validatorConfig));
        Field[] fields = FIELD_CACHE.computeIfAbsent(clazz, Class::getDeclaredFields);
//            对validator校验器的成员变量的值通过反射改成yml上的值
            injectFields(validator, fields, validatorConfig);
    }

    /**
     * 通过反射将配置值注入目标对象的字段。
     * <p>
     * 对常见类型（BigDecimal、Integer、Long、Boolean、集合）进行自动类型转换。
     * 跳过 {@code final} 字段，并对不支持的转换记录警告。
     * </p>
     *
     * @param target 要注入值的对象实例
     * @param fields 要处理的字段数组
     * @param config 包含键值对的配置映射
     */
    private void injectFields(Object target, Field[] fields, Map<String, Object> config) {
        if (config == null || config.isEmpty()) return;
        for (Field field : fields) {
            String fieldName = field.getName();
            if (!config.containsKey(fieldName)) {
                continue;
            }

            //Modifier.isFinal判断是否为final修饰
            if (Modifier.isFinal(field.getModifiers())) {
                log.warn("校验器 '{}' 中的字段 '{}' 是 final 的，无法通过反射注入值。"
                                + "请将其设为非 final 或使用 setExtParams() 进行配置。",
                        fieldName, target.getClass().getName());
                continue;
            }

            field.setAccessible(true);
            try {
                Object rawValue = config.get(fieldName);
                Object value = convertType(rawValue, field.getType());
                if (value == null && rawValue != null) {
                    log.error("字段 {} 类型转换失败，原始值：{}，目标类型：{}",
                            fieldName, rawValue, field.getType().getSimpleName());
                    continue;
                }
                Object logValue = isSensitiveField(fieldName) ? "[脱敏]" : value;
                log.debug("注入字段：{}，字段类型：{}，注入值：{}",
                        fieldName, field.getType().getSimpleName(), logValue);
//                对变量值进行修改
                field.set(target, value);
            } catch (IllegalAccessException e) {
                log.error("注入字段 [{}] 失败，非法访问：{}", fieldName, e.getMessage(), e);
            } catch (Exception e) {
                log.error("注入字段 [{}] 失败：{}", fieldName, e.getMessage(), e);
            }
        }
    }

    /**
     * 将源对象转换为指定的目标类型。
     * <p>
     * 支持以下转换：
     * </p>
     * <ul>
     *   <li>{@link BigDecimal}（从 String 或 Number）</li>
     *   <li>{@link Integer}/{@link Long}（从 Number 或数字字符串）</li>
     *   <li>{@link List}/{@link Set}（集合之间转换）</li>
     *   <li>{@link Boolean}（从 Boolean 或 "true"/"false" 字符串）</li>
     *   <li>{@link String}（toString）</li>
     * </ul>
     * <p>
     * 如果转换失败或不受支持，返回 {@code null}。
     * </p>
     *
     * @param value      要转换的源值
     * @param targetType 期望的目标类类型
     * @return 转换后的值，如果转换失败则返回 {@code null}
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        try {
            if (targetType == BigDecimal.class) {
                if (value instanceof String) {
                    String str = ((String) value).trim();
                    if (str.isEmpty()) {
                        log.warn("BigDecimal 转换失败：空字符串");
                        return null;
                    }
                    return new BigDecimal(str);
                } else if (value instanceof Number) {
                    return new BigDecimal(value.toString());
                }
            }

            if (targetType == int.class || targetType == Integer.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                } else {
                    String str = value.toString().trim();
                    if (str.matches("-?\\d+")) {
                        return Integer.parseInt(str);
                    } else {
                        log.warn("Integer 转换失败：非法字符串格式 [{}]", str);
                        return null;
                    }
                }
            }
            if (targetType == long.class || targetType == Long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                } else {
                    String str = value.toString().trim();
                    if (str.matches("-?\\d+")) {
                        return Long.parseLong(str);
                    } else {
                        log.warn("Long 转换失败：非法字符串格式 [{}]", str);
                        return null;
                    }
                }
            }

            if (targetType == Set.class && value instanceof List) {
                return new HashSet<>((List<?>) value);
            }
            if (targetType == List.class && value instanceof Set) {
                return new ArrayList<>((Set<?>) value);
            }

            if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                } else if (value instanceof String) {
                    String str = ((String) value).trim();
                    if ("true".equalsIgnoreCase(str)) {
                        return Boolean.TRUE;
                    } else if ("false".equalsIgnoreCase(str)) {
                        return Boolean.FALSE;
                    } else {
                        log.warn("Boolean 转换失败：字符串无法解析为布尔值 [{}]", str);
                        return null;
                    }
                } else {
                    log.warn("Boolean 转换失败：源类型 [{}] 无法转换为布尔值", value.getClass().getName());
                    return null;
                }
            }

            if (targetType == String.class) {
                return value.toString();
            }

            log.warn("不支持的转换：源类型 [{}] → 目标类型 [{}]，值：{}",
                    value.getClass().getName(), targetType.getSimpleName(),
                    isSensitiveValue(value) ? "[脱敏]" : value);
            return null;
        } catch (NumberFormatException e) {
            log.error("数字格式转换异常：源类型 [{}] → 目标类型 [{}]，值：{}",
                    value.getClass().getName(), targetType.getSimpleName(),
                    isSensitiveValue(value) ? "[脱敏]" : value, e);
            return null;
        } catch (Exception e) {
            log.error("类型转换失败：源类型 [{}] → 目标类型 [{}]，值：{}",
                    value.getClass().getName(), targetType.getSimpleName(),
                    isSensitiveValue(value) ? "[脱敏]" : value, e);
            return null;
        }
    }

    /**
     * 基于点分隔的路径从全局配置中提取子 Map。
     * <p>
     * 示例：路径 "validators.payment" 提取 globalConfig["validators"]["payment"]。
     * </p>
     *
     * @param configPath 点分隔的配置节点路径
     * @return 指定路径的配置 Map，如果未找到则返回空 Map
     */
    private Map<String, Object> getConfigByPath(String configPath) {
        if (globalYmlConfig == null) return new HashMap<>();
        String[] paths = configPath.split("\\.");
        Map<String, Object> current = globalYmlConfig;
        for (String path : paths) {
            Object value = current.get(path);
            if (value == null || !(value instanceof Map)) {
                return new HashMap<>();
            }
            current = (Map<String, Object>) value;
        }
        return current;
    }

    /**
     * 动态更新全局配置并重新绑定所有校验器。
     * <p>
     * 此方法用输入流中的新配置替换现有配置，并触发所有已注册校验器的完整重新绑定过程。
     * </p>
     *
     * @param newYmlStream      包含新 YAML 配置的输入流
     * @param validatorRegistry 要重新绑定的校验器映射
     * @throws RuntimeException 如果更新或绑定过程失败
     */
    public void dynamicUpdate(InputStream newYmlStream, Map<String, ValidatorHandler> validatorRegistry) {
        try {
            Yaml yaml = new Yaml();
            this.globalYmlConfig = yaml.load(newYmlStream);
            if (this.globalYmlConfig == null) {
                this.globalYmlConfig = new HashMap<>();
                log.warn("动态更新的 YAML 配置为空，初始化空配置");
                return;
            }
            log.info("开始动态 YAML 配置更新，新配置节点数：{}", globalYmlConfig.size());
            log.debug("动态更新配置详情（已脱敏）：{}", desensitizeConfig(globalYmlConfig));
            bindAllValidators(validatorRegistry);
        } catch (Exception e) {
            log.error("动态 YAML 配置更新失败", e);
            throw new RuntimeException("动态 YAML 配置更新失败", e);
        }
    }

    // ------------------------------ 日志脱敏辅助方法 ------------------------------

    /**
     * 对 YAML 内容字符串中的敏感值进行脱敏处理，用于日志输出。
     *
     * @param content 原始 YAML 内容
     * @return 脱敏后的内容字符串
     */
    private String desensitizeYmlContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return content
                .replaceAll("(salt|riskAddrs|secret|password):\\s*[^\\n]+", "$1: [脱敏]")
                .replaceAll("(codeLength|min|max|allowedChainIds):\\s*[^\\n]+", "$1: [数值脱敏]")
                .replaceAll("(list):\\s*\\[.*?\\]", "$1: [列表脱敏]");
    }

    /**
     * 递归地对配置对象中的敏感键和值进行脱敏处理。
     *
     * @param config 配置对象（Map、List 或基本类型）
     * @return 脱敏后的对象
     */
    private Object desensitizeConfig(Object config) {
        if (config == null) return null;
        if (config instanceof Map) {
            Map<String, Object> desensitizedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) config).entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                if (isSensitiveKey(key)) {
                    desensitizedMap.put(key, "[脱敏]");
                } else {
                    desensitizedMap.put(key, desensitizeConfig(value));
                }
            }
            return desensitizedMap;
        }
        if (config instanceof List) {
            return "[列表脱敏]";
        }
        return isSensitiveValue(config) ? "[脱敏]" : config;
    }

    /**
     * 检查配置键是否被视为敏感。
     *
     * @param key 配置键
     * @return 如果键敏感则返回 true，否则 false
     */
    private boolean isSensitiveKey(String key) {
        Set<String> sensitiveKeys = new HashSet<>(Arrays.asList(
                "salt", "riskAddrs", "secret", "password", "riskWalletAddrs"
        ));
        return sensitiveKeys.contains(key.toLowerCase());
    }

    /**
     * 检查字段名是否被视为敏感。
     *
     * @param fieldName 字段名
     * @return 如果字段敏感则返回 true，否则 false
     */
    private boolean isSensitiveField(String fieldName) {
        Set<String> sensitiveFields = new HashSet<>(Arrays.asList(
                "salt", "secret", "password", "riskAddrs", "codeLength"
        ));
        return sensitiveFields.contains(fieldName.toLowerCase());
    }

    /**
     * 检查值是否包含敏感关键词。
     *
     * @param value 要检查的值
     * @return 如果值敏感则返回 true，否则 false
     */
    private boolean isSensitiveValue(Object value) {
        if (value == null) return false;
        String valueStr = value.toString().toLowerCase();
        return valueStr.contains("salt") || valueStr.contains("secret") || valueStr.contains("password");
    }

    /**
     * 返回当前的全局配置映射。
     * <p>
     * 如果尚未加载配置，则返回空 Map。
     * </p>
     *
     * @return 包含全局配置键值对的映射
     */
    public Map<String, Object> getGlobalYmlConfig() {
        return this.globalYmlConfig == null ? new HashMap<>() : this.globalYmlConfig;
    }
}