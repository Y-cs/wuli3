package com.kjs.wuli3.rabbit.internal;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.spring.logging.RequestIdMdc;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** 从 RabbitMQ 消息 headers 解码传播上下文，由消费适配器显式恢复。
 *
 * 注意：作用域内同时把 requestId 写入 MDC，退出时还原消费线程进入前的值。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class RabbitContextSupport {

    private final ContextManager contextManager;
    private final ContextPropagator contextPropagator;

    /**
     * 创建 RabbitMQ 上下文支持实例。
     *
     * @param contextManager 上下文执行管理器
     * @param contextPropagator 上下文字段编码器
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public RabbitContextSupport(final ContextManager contextManager, final ContextPropagator contextPropagator) {
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager");
        this.contextPropagator = Objects.requireNonNull(contextPropagator, "contextPropagator");
    }

    /**
     * 在消息处理回调期间恢复 headers 中的传播上下文，并把 requestId 写入 MDC。
     *
     * @param headers 消息 headers（来自 {@code MessageProperties.getHeaders()}）
     * @throws ErrorCodeException 当 {@code headers} 为 {@code null} 时
     */
    @SuppressWarnings("NullAway")
    public void runInScope(final Map<String, ?> headers, final Runnable task) {
        final ContextSnapshot snapshot = this.extract(headers);
        try (final RequestIdMdc ignored = RequestIdMdc.open(snapshot)) {
            this.contextManager.from(snapshot).run(task);
        }
    }

    /** 在消息处理回调期间恢复 headers 中的传播上下文、写入 requestId，并返回结果。 */
    public <T> T callInScope(final Map<String, ?> headers, final Callable<T> task) throws Exception {
        final ContextSnapshot snapshot = this.extract(headers);
        try (final RequestIdMdc ignored = RequestIdMdc.open(snapshot)) {
            return this.contextManager.from(snapshot).call(task);
        }
    }

    @SuppressWarnings("NullAway")
    private ContextSnapshot extract(final Map<String, ?> headers) {
        Asserts.whenNull(headers).throwIllegalArgumentException("message headers must not be null");
        final Function<String, @Nullable String> fieldReader = key -> {
            final Object value = headers.get(key);
            return value == null ? null : value.toString();
        };
        return this.contextPropagator.extract(fieldReader);
    }
}
