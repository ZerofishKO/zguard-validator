package io.github.ZeroFishKoZGuard.annotation;

import java.lang.annotation.*;

/**
 * 用于将校验器绑定到 YAML 配置的注解（黑科技的核心）
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface YmlConfigBinding {
    /**
     * 该校验器在 YAML 中的配置节点路径（例如 "validatorParams.web3ChainId"）
     */
    String configPath();

    /**
     * 是否支持动态更新（默认为 true，对于紧急防御至关重要）
     */
    boolean supportDynamicUpdate() default true;
}