package com.kjs.wuli3.propagation.internal;

import com.kjs.wuli3.propagation.codec.ContextCodec;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.PropagationContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 调用链中需要传播的请求元数据。
 *
 * @param originIp 调用来源的 IP 地址
 * @param requestId 用于关联调用链路的请求标识
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public record InvocationContext(String originIp, String requestId) implements PropagationContext{

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String ORIGIN_IP = "X-Origin-Ip";

    private final static ContextCodec<InvocationContext> CODEC = new Codec();

    /**
     * 返回调用上下文的类型，用作上下文容器中的存取键。
     *
     * @return {@link InvocationContext} 的类型
     */
    @Override
    public ContextKey<? extends PropagationContext> contentKey() {
        return ContextKey.of(InvocationContext.class);
    }

    @Override
    public ContextCodec<? extends PropagationContext> contentCodec() {
        return CODEC;
    }

    public static final class Codec implements ContextCodec<InvocationContext> {

        @Override
        public ContextKey<InvocationContext> contentKey() {
            return ContextKey.of(InvocationContext.class);
        }

        @Override
        public Set<String> fieldNames() {
            return Set.of(InvocationContext.REQUEST_ID, InvocationContext.ORIGIN_IP);
        }

        @Override
        public void encode(final InvocationContext context, final BiConsumer<String, String> fieldWriter) {
            final InvocationContext actualContext = Objects.requireNonNull(context, "context");
            final BiConsumer<String, String> actualFieldWriter = Objects.requireNonNull(fieldWriter, "fieldWriter");
            actualFieldWriter.accept(InvocationContext.REQUEST_ID, actualContext.requestId());
            actualFieldWriter.accept(InvocationContext.ORIGIN_IP, actualContext.originIp());
        }

        @Override
        public Optional<InvocationContext> decode(final Function<String, @Nullable String> fieldReader) {
            Objects.requireNonNull(fieldReader, "fieldReader");
            final String requestId = fieldReader.apply(InvocationContext.REQUEST_ID);
            final String originIp = fieldReader.apply(InvocationContext.ORIGIN_IP);
            if (requestId == null || originIp == null) {
                return Optional.empty();
            }
            return Optional.of(new InvocationContext(originIp, requestId));
        }

    }
}
