package io.github.ZeroFishKo.ZGuard.pojo;

import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;

/**
 * 单个校验步骤的详细跟踪记录。
 * <p>
 * 此类捕获在特定字段上执行一个校验器的结果。
 * 它用于调试、审计和基于 AI 的规则分析。通过收集这些跟踪信息，
 * 开发人员可以准确了解校验通过或失败的原因，
 * AI 系统也可以从历史校验模式中学习。
 * </p>
 *
 * <p>每个跟踪记录包含：</p>
 * <ul>
 *   <li>被校验的字段名称，</li>
 *   <li>执行的校验器类型（键），</li>
 *   <li>校验是否通过，以及</li>
 *   <li>可选的消息（例如失败时的错误消息）。</li>
 * </ul>
 *
 * <p>实例是不可变的且线程安全的。</p>
 *
 * @see ZGuardValidator#validateWithTrace(java.util.Map)
 */
public class ValidationTrace {
    private final String field;
    private final String validatorType;
    private final boolean passed;
    private final String message;

    /**
     * 构造一个新的校验跟踪记录。
     *
     * @param field         被校验的字段名称
     * @param validatorType 执行的校验器键
     * @param passed        如果校验通过则为 {@code true}，否则为 {@code false}
     * @param message       附加消息（失败时为错误消息，可能为空）
     */
    public ValidationTrace(String field, String validatorType, boolean passed, String message) {
        this.field = field;
        this.validatorType = validatorType;
        this.passed = passed;
        this.message = message;
    }

    /**
     * 返回被校验的字段名称。
     *
     * @return 字段名称
     */
    public String getField() {
        return field;
    }

    /**
     * 返回执行的校验器键。
     *
     * @return 校验器类型（例如 "required"、"web3Wallet"）
     */
    public String getValidatorType() {
        return validatorType;
    }

    /**
     * 指示校验步骤是否通过。
     *
     * @return 如果校验通过返回 {@code true}，否则返回 {@code false}
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * 返回与此跟踪记录关联的消息。
     * <p>
     * 对于失败的校验，这通常是错误消息。
     * 对于成功的校验，可能为空字符串。
     * </p>
     *
     * @return 消息（永远不为 {@code null}）
     */
    public String getMessage() {
        return message;
    }
}