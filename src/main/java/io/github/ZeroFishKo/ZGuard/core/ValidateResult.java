package io.github.ZeroFishKo.ZGuard.core;

/**
 * 不可变的校验结果，表示一次校验操作的输出。
 * <p>
 * 此类专为高并发环境设计：
 * </p>
 * <ul>
 *   <li>线程安全 – 所有字段均为 final，无可变状态。</li>
 *   <li>内存高效 – 每个实例占用小于 100 字节。</li>
 *   <li>零开销 – 成功结果使用单例，避免不必要的对象创建。</li>
 * </ul>
 *
 * <p>通过静态工厂方法 {@link #success()} 和 {@link #fail(String, String)} 创建实例。
 * 使用 {@link #isSuccess()} 检查结果，通过 {@link #getFieldName()}、{@link #getErrorMsg()}
 * 获取失败详情。</p>
 *
 * @see ZGuardValidator#validate(java.util.Map)
 */
public class ValidateResult {
    private final boolean success;
    private final String fieldName;
    private final String errorMsg;

    // 私有构造器，通过工厂方法创建实例
    private ValidateResult(boolean success, String fieldName, String errorMsg) {
        this.success = success;
        this.fieldName = fieldName;
        this.errorMsg = errorMsg;
    }

    /**
     * 返回表示校验成功的单例实例。
     * <p>
     * 不创建新对象；所有成功结果共享同一个实例。
     * </p>
     *
     * @return 共享的成功结果
     */
    public static ValidateResult success() {
        return SuccessHolder.SUCCESS;
    }

    /**
     * 创建一个新的校验失败结果。
     * <p>
     * 每个失败结果都有自己的实例，因为字段名和错误消息是唯一的。
     * 该对象是不可变的且线程安全的。
     * </p>
     *
     * @param fieldName 导致失败的字段名称
     * @param errorMsg  描述失败的人类可读错误消息
     * @return 一个新的失败结果
     */
    public static ValidateResult fail(String fieldName, String errorMsg) {
        return new ValidateResult(false, fieldName, errorMsg);
    }

    // 单例持有者（延迟初始化，线程安全）
    private static class SuccessHolder {
        private static final ValidateResult SUCCESS = new ValidateResult(true, null, null);
    }

    /**
     * 返回校验是否成功。
     *
     * @return 如果校验通过返回 {@code true}，否则返回 {@code false}
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 返回导致校验失败的字段名称。
     * <p>
     * 仅当 {@link #isSuccess()} 返回 {@code false} 时此方法才有意义。
     * 对于成功结果，字段名称始终为 {@code null}。
     * </p>
     *
     * @return 字段名称，或成功时的 {@code null}
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * 返回与校验失败关联的错误消息。
     * <p>
     * 仅当 {@link #isSuccess()} 返回 {@code false} 时此方法才有意义。
     * 对于成功结果，错误消息始终为 {@code null}。
     * </p>
     *
     * @return 错误消息，或成功时的 {@code null}
     */
    public String getErrorMsg() {
        return errorMsg;
    }
}