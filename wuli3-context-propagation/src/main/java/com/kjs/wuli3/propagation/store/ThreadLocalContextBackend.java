package com.kjs.wuli3.propagation.store;

import com.kjs.wuli3.propagation.context.Context;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextState;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 基于 ThreadLocal 提供上下文读取及可嵌套、异常安全的作用域绑定。
 *
 * @author GuoYang create on 2026/9/28 15:00
 */
public final class ThreadLocalContextBackend implements ContextReader, ContextBinder {
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<ContextState> holder = new ThreadLocal<>();

    /** 创建独立的线程上下文后端；读取和绑定共享同一份内部存储。 */
    public ThreadLocalContextBackend() {}

    /** 读取当前绑定中指定类型的上下文。 */
    @Override
    public <T extends Context> Optional<T> get(final ContextKey<T> key) {
        return this.state().get(key);
    }

    /** 读取当前完整状态；未绑定时返回空状态。 */
    @Override
    public ContextState state() {
        final ContextState current = this.holder.get();
        return current == null ? ContextState.empty() : current;
    }

    /** 执行任务并在结束后恢复进入前的状态。 */
    @Override
    public void run(final ContextState state, final Runnable task) {
        Objects.requireNonNull(task, "task");
        final ContextState previous = this.replaceState(Objects.requireNonNull(state, "state"));
        try {
            task.run();
        } finally {
            this.replaceState(previous);
        }
    }

    /** 计算结果并在结束后恢复进入前的状态。 */
    @Override
    public <T> T call(final ContextState state, final Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task");
        final ContextState previous = this.replaceState(Objects.requireNonNull(state, "state"));
        try {
            return task.call();
        } finally {
            this.replaceState(previous);
        }
    }

    /** 替换线程状态并返回旧值；仅供本后端建立和恢复作用域使用。 */
    private ContextState replaceState(final ContextState state) {
        final ContextState previous = this.state();
        if (state.isEmpty()) {
            this.holder.remove();
        } else {
            this.holder.set(state);
        }
        return previous;
    }
}
