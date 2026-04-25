package io.github.ZeroFishKoZGuard.impl.web3;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.YmlConfigBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Web3 链 ID 校验器。
 * <p>
 * 此校验器检查给定的链 ID 是否在配置的白名单中。
 * 白名单可通过 {@link #setExtParams(Map)} 或 YAML 绑定动态更新。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * validatorParams:
 *   web3ChainId:
 *     allowedChainIds: [56, 97, 1000, 137]
 * </pre>
 *
 * @see ValidatorHandler
 * @see YmlConfigBinding
 */
@YmlConfigBinding(configPath = "validatorParams.web3ChainId")
public class Web3ChainIdValidator implements ValidatorHandler {
    private static final Logger log = LoggerFactory.getLogger(Web3ChainIdValidator.class);
    private Set<String> allowedChainIds = Collections.emptySet();

    /**
     * 校验给定的链 ID。
     *
     * @param value 链 ID，可以是 String 或任何 {@code toString()} 返回 ID 的对象
     * @return 如果链 ID 在白名单中返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null) return false;
        String chainId = value instanceof String ? ((String) value).trim() : value.toString().trim();
        log.debug("Web3ChainIdValidator 校验：chainId={}, allowedList={}", chainId, allowedChainIds);
        if (allowedChainIds.isEmpty()) {
            log.warn("Web3ChainIdValidator 白名单为空，拒绝所有链 ID");
            return false;
        }
        for (Object id : allowedChainIds) {
            if (id.toString().trim().equals(chainId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 包含当前白名单的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "无效的 Web3 链 ID（允许的链：" + allowedChainIds + "）";
    }

    @Override
    public Object[] getMessageArguments() {
        return new Object[]{allowedChainIds};
    }

    /**
     * 此校验器的优先级（1 = 最高）。
     *
     * @return 优先级值
     */
    @Override
    public int getPriority() {
        return 1;
    }

    /**
     * 此校验器的唯一键。
     *
     * @return "web3ChainId"
     */
    @Override
    public String getValidatorKey() {
        return "web3ChainId";
    }

    /**
     * 动态更新链 ID 的白名单。
     * <p>
     * 期望一个包含键 {@code "allowedChainIds"} 的映射。
     * 值可以是 {@link List}、{@link Set} 或 {@code String[]} 类型的链 ID。
     * 所有条目都会被 trim 并存储为字符串。
     * </p>
     *
     * @param extParams 包含新白名单的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        log.debug("Web3ChainIdValidator 接收到参数：{}", extParams);
        if (extParams == null || !extParams.containsKey("allowedChainIds")) {
            log.warn("未收到参数 'allowedChainIds'");
            return;
        }

        Object idsObj = extParams.get("allowedChainIds");
        Set<String> newChainIds = new HashSet<>();

        if (idsObj instanceof List) {
            for (Object obj : (List<?>) idsObj) {
                if (obj != null) {
                    newChainIds.add(obj.toString().trim());
                }
            }
        } else if (idsObj instanceof Set) {
            for (Object obj : (Set<?>) idsObj) {
                if (obj != null) {
                    newChainIds.add(obj.toString().trim());
                }
            }
        } else if (idsObj instanceof String[]) {
            for (String s : (String[]) idsObj) {
                if (s != null) {
                    newChainIds.add(s.trim());
                }
            }
        } else {
            log.error("allowedChainIds 的类型不支持：{}", idsObj.getClass().getName());
            return;
        }

        this.allowedChainIds = Collections.unmodifiableSet(newChainIds);
        log.info("Web3ChainIdValidator 白名单已更新：{}（共 {} 个有效链 ID）",
                allowedChainIds, allowedChainIds.size());
    }
}