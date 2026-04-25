package io.github.ZeroFishKo.ZGuard.annotation;

import java.lang.annotation.*;

/**
 * 字段级 YAML 配置映射注解
 * （支持字段名与 YAML 键名不同的场景）
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface YmlFieldMapping {
    String value(); // YAML 键名
}