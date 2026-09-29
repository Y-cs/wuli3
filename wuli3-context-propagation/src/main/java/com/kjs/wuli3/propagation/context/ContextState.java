package com.kjs.wuli3.propagation.context;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.kjs.wuli3.core.assertion.Asserts;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * 当前执行作用域中的不可变完整上下文状态。
 *
 * <p>上下文实例按引用保存，调用方须遵守 {@link Context} 的不可变约定。状态按上下文类型及传播资格分区，
 * 使快照捕获可以直接读取传播分区，不必再次遍历全部上下文。
 *
 * @author GuoYang create on 2026/9/28 11:00
 */
public final class ContextState {

    private static final ContextState EMPTY = new ContextState(ImmutableTable.of());

    private final ImmutableTable<ContextPartition, ContextKey<? extends Context>, Context> contexts;

    private ContextState(final Table<ContextPartition, ContextKey<? extends Context>, Context> contexts) {
        this.contexts = ImmutableTable.copyOf(contexts);
    }

    /** 返回空状态。 */
    public static ContextState empty() {
        return ContextState.EMPTY;
    }

    /** 从上下文创建状态；同类型上下文以后者为准。 */
    public static ContextState of(final Context... contexts) {
        Objects.requireNonNull(contexts, "contexts");
        final Table<ContextPartition, ContextKey<? extends Context>, Context> values = HashBasedTable.create();
        for (final Context context : contexts) {
            final Context actual = Objects.requireNonNull(context, "context");
            final ContextKey<? extends Context> key = Objects.requireNonNull(actual.contentKey(), "context.contentKey()");
            values.put(key.isPropagatable() ? ContextPartition.PROPAGATION : ContextPartition.LOCAL, key, actual);
        }
        return values.isEmpty() ? ContextState.EMPTY : new ContextState(values);
    }

    /** 返回指定类型的上下文。 */
    public <T extends Context> Optional<T> get(final ContextKey<T> type) {
        final ContextKey<T> actualType = Objects.requireNonNull(type, "type");
        final ContextPartition partition = actualType.isPropagatable()
                ? ContextPartition.PROPAGATION
                : ContextPartition.LOCAL;
        final Context value = this.contexts.get(partition, actualType);
        return value == null ? Optional.empty() : Optional.of(actualType.cast(value));
    }

    /** 返回绑定上下文后的新状态。 */
    public <T extends Context> ContextState with(final T context) {
        final T actual = Objects.requireNonNull(context, "context");
        final ContextKey<? extends Context> key =
                Objects.requireNonNull(actual.contentKey(), "context.contentKey()");
        final Table<ContextPartition, ContextKey<? extends Context>, Context> table = HashBasedTable.create(this.contexts);
        table.put(key.isPropagatable() ? ContextPartition.PROPAGATION : ContextPartition.LOCAL, key, actual);
        return new ContextState(table);
    }

    /** 返回删除指定类型后的新状态。 */
    public ContextState without(final ContextKey<? extends Context> type) {
        final ContextKey<? extends Context> actualType = Objects.requireNonNull(type, "type");
        final Table<ContextPartition, ContextKey<? extends Context>, Context> table = HashBasedTable.create(this.contexts);
        table.remove(actualType.isPropagatable() ? ContextPartition.PROPAGATION : ContextPartition.LOCAL, actualType);
        return table.isEmpty() ? ContextState.EMPTY : new ContextState(table);
    }

    /** 返回全部上下文的不可变集合。 */
    public Collection<Context> values() {
        return this.contexts.values();
    }

    /** 返回可传播上下文的不可变集合；供同包快照实现直接捕获。 */
    public Collection<Context> propagationValues() {
        return this.contexts.row(ContextPartition.PROPAGATION).values();
    }

    /** 判断状态是否为空。 */
    public boolean isEmpty() {
        return this.contexts.isEmpty();
    }

    private enum ContextPartition {
        LOCAL,
        PROPAGATION
    }
}
