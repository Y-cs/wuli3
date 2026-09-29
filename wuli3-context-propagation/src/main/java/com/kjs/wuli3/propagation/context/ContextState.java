package com.kjs.wuli3.propagation.context;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 当前执行作用域中的不可变完整上下文状态。
 *
 * <p>上下文实例按引用保存，调用方须遵守 {@link Context} 的不可变约定。
 *
 * @author GuoYang create on 2026/9/28 11:00
 */
public final class ContextState {

    private static final ContextState EMPTY = new ContextState(Map.of());

    private final Map<ContextKey<? extends Context>, Context> contexts;

    private ContextState(final Map<ContextKey<? extends Context>, Context> contexts) {
        this.contexts = Map.copyOf(contexts);
    }

    /** 返回空状态。 */
    public static ContextState empty() {
        return ContextState.EMPTY;
    }

    /** 从上下文创建状态。 */
    public static ContextState of(final Context... contexts) {
        final Map<ContextKey<? extends Context>, Context> values = new HashMap<>();
        for (final Context context : contexts) {
            final Context actual = Objects.requireNonNull(context, "context");
            values.put(Objects.requireNonNull(actual.contentKey(), "context.contentKey()"), actual);
        }
        return values.isEmpty() ? ContextState.EMPTY : new ContextState(values);
    }

    /** 返回指定类型的上下文。 */
    public <T extends Context> Optional<T> get(final ContextKey<T> type) {
        final ContextKey<T> actualType = Objects.requireNonNull(type, "type");
        final Context value = this.contexts.get(actualType);
        return value == null ? Optional.empty() : Optional.of(actualType.cast(value));
    }

    /** 返回绑定上下文后的新状态。 */
    public <T extends Context> ContextState with(final T context) {
        final T actual = Objects.requireNonNull(context, "context");
        final Map<ContextKey<? extends Context>, Context> values = new HashMap<>(this.contexts);
        values.put(Objects.requireNonNull(actual.contentKey(), "context.contentKey()"), actual);
        return new ContextState(values);
    }

    /** 返回删除指定类型后的新状态。 */
    public ContextState without(final ContextKey<? extends Context> type) {
        final Map<ContextKey<? extends Context>, Context> values = new HashMap<>(this.contexts);
        values.remove(Objects.requireNonNull(type, "type"));
        return values.isEmpty() ? ContextState.EMPTY : new ContextState(values);
    }

    /** 返回全部上下文的不可变集合。 */
    public Collection<Context> values() {
        return this.contexts.values();
    }

    /** 判断状态是否为空。 */
    public boolean isEmpty() {
        return this.contexts.isEmpty();
    }
}
