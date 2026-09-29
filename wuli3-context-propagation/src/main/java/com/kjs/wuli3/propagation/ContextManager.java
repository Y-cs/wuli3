package com.kjs.wuli3.propagation;

import com.kjs.wuli3.propagation.context.Context;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import java.util.Objects;

/**
 * 创建持有确定状态的上下文代理，并捕获当前可传播上下文。
 *
 * @author GuoYang create on 2026/9/28 15:00
 */
public final class ContextManager {
    private final ContextReader reader;
    private final ContextBinder binder;

    /** 使用读取能力及与其配套的绑定能力创建管理入口。 */
    public ContextManager(final ContextReader reader, final ContextBinder binder) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.binder = Objects.requireNonNull(binder, "binder");
    }

    /** 返回当前执行中生效的完整状态。 */
    public ContextState state() {
        return this.reader.state();
    }

    /** 创建代理这一刻的完整状态，包括本地上下文。 */
    public ContextProxy withCurrentState() {
        return new ContextProxy(this.reader.state(), this.binder);
    }

    /** 创建持有完整状态的代理；创建时不会改变当前上下文。 */
    public ContextProxy with(final ContextState state) {
        return new ContextProxy(state, this.binder);
    }

    /** 从指定上下文集合创建代理，不隐式继承当前状态。 */
    public ContextProxy with(final Context... contexts) {
        return this.with(ContextState.of(contexts));
    }

    /** 从传播快照创建代理，不合并执行线程已有上下文。 */
    public ContextProxy from(final ContextSnapshot snapshot) {
        return this.with(Objects.requireNonNull(snapshot, "snapshot").toState());
    }

    /** 捕获当前状态中允许跨边界传播的上下文。 */
    public ContextSnapshot capture() {
        return ContextSnapshot.from(this.state());
    }
}
