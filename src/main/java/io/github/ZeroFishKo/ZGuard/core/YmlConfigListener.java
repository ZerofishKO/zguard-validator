package io.github.ZeroFishKo.ZGuard.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * 配置中心监听器（兼容 Nacos/Apollo，触发动态更新）
 */
public class YmlConfigListener {

    private static final Logger log = LoggerFactory.getLogger(ZGuardValidator.class);
    private final ZGuardValidator validator;
    private final YmlConfigBinder configBinder;

    public YmlConfigListener(ZGuardValidator validator, YmlConfigBinder configBinder) {
        this.validator = validator;
        this.configBinder = configBinder;
    }

    /**
     * 配置中心 YAML 变更回调（无锁动态更新触发器）
     */
    public void onYmlChanged(InputStream newYmlStream) {
        synchronized (this) { // 仅在更新期间加锁，耗时 <1ms
            // 1. 动态更新校验器配置
            configBinder.dynamicUpdate(newYmlStream, validator.getValidatorRegistry());
            // 2. 重建预编译的组合步骤（原框架方法）
            validator.rebuildCombineSteps();
        }
        log.info("YAML 配置动态更新完成，新规则已生效");
    }
}