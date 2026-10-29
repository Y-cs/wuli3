package com.kjs.wuli3.openfeign.error;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 把 HTTP 响应头中的稳定错误协议还原为唯一的 {@link ErrorCodeException}。
 *
 * 注意：错误头缺失或非法时交给 Feign 默认解码器，保留原始 {@link feign.FeignException}；
 * 只有调用方使用支持错误头传播的 Web 出口时才会命中转换。
 *
 * @author lipt create on 2026/10/29 18:21
 */
public final class FeignErrorDecoder implements ErrorDecoder {

    private static final ErrorCodePropagator ERROR_PROPAGATOR = new ErrorCodePropagator();

    private final ErrorDecoder fallback = new ErrorDecoder.Default();

    /** 优先返回远程错误语义，无法还原时保持 Feign 默认行为。 */
    @Override
    @SuppressWarnings("NullAway")
    public Exception decode(final String methodKey, final Response response) {
        final Map<String, String> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        response.headers().forEach((name, values) -> {
            if (!values.isEmpty()) {
                fields.put(name, values.iterator().next());
            }
        });
        final Optional<ErrorCodeCarrier> protocol = ERROR_PROPAGATOR.extract(fields::get);
        if (protocol.isPresent()) {
            final ErrorCodeCarrier carrier = protocol.get();
            return new ErrorCodeException(carrier, carrier.message(), this.fallback.decode(methodKey, response));
        }
        return this.fallback.decode(methodKey, response);
    }
}
