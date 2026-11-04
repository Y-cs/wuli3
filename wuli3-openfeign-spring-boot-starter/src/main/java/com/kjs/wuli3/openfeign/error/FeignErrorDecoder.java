package com.kjs.wuli3.openfeign.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 把 Web 统一错误响应体中的展示错误码还原为 {@link ErrorCodeException}。
 *
 * <p>只读取响应体的 {@code code} 和 {@code message}，与提供方对外展示的内容保持一致。响应体不携带责任归属、
 * 严重程度和来源服务，缺失部分按 HTTP 状态推导：4xx 视为调用方错误，5xx 视为服务方错误。这与
 * {@link ErrorOrigin} 声明的适配层映射规则一致。
 *
 * <p>注意：响应体缺失、非 JSON、缺少 {@code code} 或使用成功码时保持 {@link FeignException}，不根据不完整数据
 * 推断业务错误。解码结果通过异常抛给调用方，由调用方的错误出口按责任和严重度统一记录，这里不重复记录日志。
 *
 * @author lipt create on 2026/11/4 11:20
 */
public final class FeignErrorDecoder implements ErrorDecoder {

    /** Web 统一响应中表示成功的错误码，与提供方 {@code ApiResponse.SUCCESS_CODE} 保持一致。 */
    private static final String SUCCESS_CODE = "0";

    private final ObjectMapper objectMapper;
    private final ErrorDecoder fallback = new ErrorDecoder.Default();

    /** 创建使用指定 JSON 解析器的错误解码器。 */
    public FeignErrorDecoder(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** 优先返回远程错误语义，无法还原时保持 Feign 默认行为。 */
    @Override
    @SuppressWarnings("NullAway")
    public Exception decode(final String methodKey, final Response response) {
        final byte[] body = FeignErrorDecoder.body(response);
        // 先复制响应体再交给默认解码器，避免两处读取同一个流导致后读的一方拿到空内容。
        final Response replayable =
                body == null ? response : response.toBuilder().body(body).build();
        final Exception original = this.fallback.decode(methodKey, replayable);
        final ErrorCodeCarrier remote = this.remoteError(replayable, body);
        return remote == null ? original : new ErrorCodeException(remote, remote.message(), original);
    }

    /** 读取响应体字节；缺失或不可读时返回 {@code null}。 */
    private static byte @Nullable [] body(final Response response) {
        final Response.Body body = response.body();
        if (body == null) {
            return null;
        }
        try (InputStream stream = body.asInputStream()) {
            return stream.readAllBytes();
        } catch (final IOException unreadable) {
            return null;
        }
    }

    /**
     * 从 JSON 响应体读取展示错误码；非错误响应体返回 {@code null}。
     */
    private @Nullable ErrorCodeCarrier remoteError(final Response response, final byte @Nullable [] body) {
        if (body == null || body.length == 0 || !FeignErrorDecoder.json(response)) {
            return null;
        }
        final JsonNode payload;
        try {
            payload = this.objectMapper.readTree(body);
        } catch (final IOException | RuntimeException malformed) {
            return null;
        }
        final String code = payload.path("code").asText("");
        if (code.isBlank() || FeignErrorDecoder.SUCCESS_CODE.equals(code)) {
            return null;
        }
        return new ErrorCodeCarrier(
                code,
                code,
                payload.path("message").asText(""),
                FeignErrorDecoder.origin(response.status()),
                ErrorSeverity.NORMAL,
                "",
                ErrorVisibility.PUBLIC);
    }

    /** 4xx 归属调用方，5xx 归属服务方；其他状态按调用方处理。 */
    private static ErrorOrigin origin(final int status) {
        return status >= 500 ? ErrorOrigin.SERVER : ErrorOrigin.CALLER;
    }

    /** 判断响应是否为 JSON，避免对二进制或 HTML 错误页做无意义的解析。 */
    private static boolean json(final Response response) {
        for (final Map.Entry<String, Collection<String>> entry :
                response.headers().entrySet()) {
            if (!"Content-Type".equalsIgnoreCase(entry.getKey())
                    || entry.getValue().isEmpty()) {
                continue;
            }
            return entry.getValue().iterator().next().toLowerCase(Locale.ROOT).contains("json");
        }
        return false;
    }
}
