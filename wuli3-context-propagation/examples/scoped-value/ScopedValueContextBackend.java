package com.kjs.wuli3.propagation.example;

import com.kjs.wuli3.propagation.context.Context;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 使用 JDK 21 预览版 ScopedValue 提供配套的上下文读取和作用域绑定能力。
 *
 * <p>注意：仅作为独立编译示例，不纳入生产源码；Reader 和 Binder 必须使用同一个实例。
 * 结构化子任务可以继承完整绑定，传播筛选须由调用方在创建结构化任务作用域之前完成。
 *
 * @author 国杨 create on 2026/9/28 16:00
 */
public final class ScopedValueContextBackend implements ContextReader, ContextBinder {

    private final ScopedValue<ContextState> current = ScopedValue.newInstance();

    /** 按键读取当前绑定，不存在时返回空值。 */
    @Override
    public <T extends Context> Optional<T> get(final ContextKey<T> key) {
        return this.state().get(key);
    }

    /** 返回当前完整绑定；尚未进入绑定作用域时返回空状态。 */
    @Override
    public ContextState state() {
        return this.current.isBound() ? this.current.get() : ContextState.empty();
    }

    /** 在指定状态下执行任务，退出时由 ScopedValue 自动恢复外层绑定。 */
    @Override
    public void run(final ContextState state, final Runnable task) {
        ScopedValue.where(this.current, Objects.requireNonNull(state, "state"))
                .run(Objects.requireNonNull(task, "task"));
    }

    /** 在指定状态下计算结果，退出时恢复外层绑定并原样传播任务异常。 */
    @Override
    public <T> T call(final ContextState state, final Callable<T> task) throws Exception {
        return ScopedValue.where(this.current, Objects.requireNonNull(state, "state"))
                .call(Objects.requireNonNull(task, "task"));
    }
}
