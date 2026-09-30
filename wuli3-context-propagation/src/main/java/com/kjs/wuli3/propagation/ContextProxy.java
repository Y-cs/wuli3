package com.kjs.wuli3.propagation;

import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.store.ContextBinder;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 持有确定状态，并在每次任务执行期间绑定该状态的不可变代理。
 *
 * <p>包装任务不会再次捕获上下文；代理绑定完整状态，跨边界传播应先由管理入口捕获快照。
 *
 * @author GuoYang create on 2026/9/28 15:00
 */
public final class ContextProxy {
    private final ContextState state;
    private final ContextBinder binder;

    /** 创建持有指定状态及绑定能力的代理，不改变当前执行状态。 */
    public ContextProxy(final ContextState state, final ContextBinder binder) {
        this.state = Objects.requireNonNull(state, "state");
        this.binder = Objects.requireNonNull(binder, "binder");
    }

    /** 返回代理准备执行的状态，可能不同于当前生效状态。 */
    public ContextState state() {
        return this.state;
    }

    /** 在代理状态中执行任务，结束或失败后恢复外层状态。 */
    public void run(final Runnable task) {
        this.binder.run(this.state, Objects.requireNonNull(task, "task"));
    }

    /** 在代理状态中计算结果，结束或失败后恢复外层状态。 */
    public <T> T call(final Callable<T> task) throws Exception {
        return this.binder.call(this.state, Objects.requireNonNull(task, "task"));
    }

    /** 包装任务，使其每次执行时使用代理持有的状态。 */
    public Runnable wrap(final Runnable task) {
        Objects.requireNonNull(task, "task");
        return () -> this.run(task);
    }

    /** 包装可调用任务，使其每次执行时使用代理持有的状态。 */
    public <T> Callable<T> wrap(final Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return () -> this.call(task);
    }

    /** 包装供应器，使其每次执行时使用代理持有的状态。 */
    public <T> Supplier<T> wrapSupplier(final Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        return () -> {
            try {
                return this.call(task::get);
            } catch (final RuntimeException exception) {
                throw exception;
            } catch (final Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
    }
}
