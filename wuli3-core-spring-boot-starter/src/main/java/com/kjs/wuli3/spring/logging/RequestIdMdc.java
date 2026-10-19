package com.kjs.wuli3.spring.logging;

import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * 在入站协议传播上下文的作用域内把 requestId 绑定到 MDC，关闭时还原进入前的值。
 *
 * 注意：只操作 requestId 一个字段，不触碰由 OpenTelemetry Agent 管理的 trace_id、span_id；入站线程来自线程池，
 * 因此退出时按进入前的值还原而不是无条件移除。快照中没有 {@link InvocationContext} 时不写 MDC，关闭为空操作。
 *
 * 使用方式：入站适配器解出快照后，用本作用域包住上下文绑定与业务回调。
 *
 * @author lipt create on 2026/10/16 18:56
 */
public final class RequestIdMdc implements AutoCloseable {

    /** 快照中没有请求上下文时使用的空作用域。 */
    private static final RequestIdMdc INACTIVE = new RequestIdMdc(false, null);

    /** 是否已把 requestId 写入 MDC；为 false 时关闭不做任何处理。 */
    private final boolean active;

    /** 进入前的 MDC 值；为 null 表示关闭时需要移除该字段。 */
    private final @Nullable String previous;

    private RequestIdMdc(final boolean active, final @Nullable String previous) {
        this.active = active;
        this.previous = previous;
    }

    /**
     * 从传播快照中取出 requestId 并写入 MDC；快照中没有请求上下文时不写。
     *
     * @param snapshot 入站协议解出的传播快照
     * @return 关闭后还原进入前 MDC 值的作用域
     * @throws NullPointerException 当 {@code snapshot} 为 {@code null} 时
     */
    public static RequestIdMdc open(final ContextSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        final Optional<InvocationContext> context = snapshot.get(ContextKey.of(InvocationContext.class));
        if (context.isEmpty()) {
            return RequestIdMdc.INACTIVE;
        }
        final @Nullable String previous = MDC.get(LogMdcKeys.REQUEST_ID);
        MDC.put(LogMdcKeys.REQUEST_ID, context.get().requestId());
        return new RequestIdMdc(true, previous);
    }

    /** 还原进入前的 MDC 值；重复关闭按相同值还原或移除，不产生额外影响。 */
    @Override
    public void close() {
        if (!this.active) {
            return;
        }
        if (this.previous == null) {
            MDC.remove(LogMdcKeys.REQUEST_ID);
        } else {
            MDC.put(LogMdcKeys.REQUEST_ID, this.previous);
        }
    }
}
