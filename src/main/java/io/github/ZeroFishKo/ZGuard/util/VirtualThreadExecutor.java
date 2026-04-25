package io.github.ZeroFishKo.ZGuard.util;

import io.github.ZeroFishKo.ZGuard.core.ZGuardValidator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用于创建线程池的工厂，可自动适配 JDK 版本。
 * <p>
 * 此类动态检测运行时环境是否支持虚拟线程（JDK 21 引入）。
 * 如果支持，它创建一个执行器，每个任务启动一个新虚拟线程，
 * 使用 {@link Executors#newVirtualThreadPerTaskExecutor()}。
 * 否则，回退到大小等于可用处理器数量的固定线程池。
 * </p>
 *
 * <p>虚拟线程提供轻量级并发，非常适合 I/O 密集型任务，
 * 而固定线程池适用于 CPU 密集型工作。通过使用此类，
 * 框架可以利用最佳可用线程模型，而无需对 JDK 21 API 产生编译时依赖。</p>
 *
 * <p>此类使用反射访问虚拟线程执行器工厂方法，
 * 确保与 JDK 8 和 17 的兼容性（这些版本中没有这些 API）。</p>
 *
 * @see java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()
 * @see ZGuardValidator#batchValidate(java.util.List)
 */
public class VirtualThreadExecutor {
    // 用于创建虚拟线程每任务执行器的反射方法
    private static final java.lang.reflect.Method NEW_VIRTUAL_THREAD_POOL_METHOD;
    // 是否支持虚拟线程（JDK 21+）
    private static final boolean SUPPORTS_VIRTUAL_THREAD;

    static {
        boolean supports = false;
        java.lang.reflect.Method method = null;
        try {
            // 仅在 JDK 21+ 上尝试加载虚拟线程 API，低版本静默忽略
            Class<?> executorsClass = Class.forName("java.util.concurrent.Executors");
            method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
            supports = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // 不可用 – 在 JDK 8/17 上预期如此
        }
        SUPPORTS_VIRTUAL_THREAD = supports;
        NEW_VIRTUAL_THREAD_POOL_METHOD = method;
    }

    /**
     * 根据 JDK 版本创建适当的 {@link ExecutorService}。
     * <ul>
     *   <li>在 JDK 21 或更高版本上：返回一个执行器，每个任务创建一个新的虚拟线程
     *       （通过 {@code Executors.newVirtualThreadPerTaskExecutor()}）。</li>
     *   <li>在 JDK 8 或 17 上：返回一个大小等于可用处理器数量的固定线程池
     *       （通过 {@link Executors#newFixedThreadPool(int)}）。</li>
     * </ul>
     * <p>
     * 如果在 JDK 21+ 上反射意外失败，方法回退到固定线程池。
     * </p>
     *
     * @return 准备提交任务的 {@link ExecutorService}
     */
    public static ExecutorService createExecutor() {
        if (SUPPORTS_VIRTUAL_THREAD) {
            try {
                // 通过反射调用以避免编译时依赖
                return (ExecutorService) NEW_VIRTUAL_THREAD_POOL_METHOD.invoke(null);
            } catch (Exception e) {
                // 反射失败时回退到固定线程池
                return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            }
        } else {
            return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }
    }
}