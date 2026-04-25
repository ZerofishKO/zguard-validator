package io.github.ZeroFishKoZGuard.impl.web3;

import io.github.ZeroFishKoZGuard.Interface.ValidatorHandler;
import io.github.ZeroFishKoZGuard.annotation.YmlConfigBinding;
import io.github.ZeroFishKoZGuard.annotation.YmlFieldMapping;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;

/**
 * 检查钱包地址是否属于风险列表的校验器。
 * <p>
 * 此校验器用于阻止向高风险地址的交易。
 * 风险列表通过 YAML 在 {@code riskLists.riskWalletAddrs} 下配置，
 * 并且可以动态更新。
 * </p>
 *
 * <p>YAML 配置示例：</p>
 * <pre>
 * riskLists:
 *   riskWalletAddrs:
 *     list: ["0x1234...", "0x5678..."]
 * </pre>
 *
 * <p>如果风险列表为空，则所有地址都被视为安全（校验通过）。</p>
 *
 * @see ValidatorHandler
 * @see YmlConfigBinding
 * @see YmlFieldMapping
 */
@YmlConfigBinding(configPath = "riskLists.riskWalletAddrs")
public class RiskAddrValidator implements ValidatorHandler {
    @YmlFieldMapping("list")
    private Set<String> riskAddrs = Collections.emptySet();

    /**
     * 根据风险列表校验钱包地址。
     *
     * @param value 钱包地址字符串
     * @return 如果地址不在风险列表中（或列表为空）返回 {@code true}；
     *         如果地址在列表中返回 {@code false}
     */
    @Override
    public boolean validate(Object value) {
        if (value == null || !(value instanceof String)) return false;
        String walletAddr = (String) value;
        // 快速失败：风险列表为空则通过；否则如果地址在列表中则阻止
        return riskAddrs.isEmpty() || !riskAddrs.contains(walletAddr);
    }

    /**
     * 校验失败时返回的错误消息。
     *
     * @return 表示地址被阻止的错误消息
     */
    @Override
    public String getErrorMessage() {
        return "钱包地址在风险列表中，操作被拒绝";
    }

    /**
     * 此校验器的优先级（1 = 最高）。应尽早运行以快速拒绝风险地址。
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
     * @return "riskAddr"
     */
    @Override
    public String getValidatorKey() {
        return "riskAddr";
    }

    /**
     * 动态更新风险地址列表。
     * <p>
     * 期望一个包含键 {@code "list"} 的映射。
     * 值必须是地址字符串的 {@link List} 或 {@link Set}。
     * 新列表存储为不可变集合。
     * </p>
     *
     * @param extParams 包含新风险列表的映射
     */
    @Override
    public void setExtParams(Map<String, Object> extParams) {
        if (extParams != null && extParams.containsKey("list")) {
            Object addrsObj = extParams.get("list");
            if (addrsObj instanceof List) {
                List<String> addrsList = (List<String>) addrsObj;
                this.riskAddrs = Collections.unmodifiableSet(new HashSet<>(addrsList));
            } else if (addrsObj instanceof Set) {
                this.riskAddrs = Collections.unmodifiableSet((Set<String>) addrsObj);
            }
        }
    }
}