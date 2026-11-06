package com.kjs.wuli3.openfeign.client.error;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import com.kjs.wuli3.json.provider.JacksonProvider;
import com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * 有界读取内部失败响应，只从版本、媒体类型和完整字段均合法的文档还原业务错误。
 *
 * 注意：不加载远程异常类型，也不执行公开 Web 的错误可见性投影。
 * 不兼容或损坏的响应保留为 FeignException，解析及读取故障以 suppressed 异常保留诊断原因。
 * 本类同时供 Feign ErrorDecoder 和调用链前置校验使用，两条路径共享相同的协议判断。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
@NullMarked
public final class HttpRpcErrorDecoder implements ErrorDecoder {

    private static final List<String> FIELDS = List.of(
            ErrorCodePropagator.CODE,
            ErrorCodePropagator.ORIGINAL_CODE,
            ErrorCodePropagator.MESSAGE,
            ErrorCodePropagator.ORIGIN,
            ErrorCodePropagator.SEVERITY,
            ErrorCodePropagator.VISIBILITY,
            ErrorCodePropagator.SOURCE_SERVICE);
    private final ObjectMapper mapper = JacksonProvider.newJsonMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private final ErrorCodePropagator propagator = new ErrorCodePropagator();

    /**
     * 最多读取协议上限加一个字节，关闭读取的输入流，并返回业务异常或保留正文的传输异常。
     *
     * 注意：流读取或关闭失败时不使用可能不完整的文档；调用方仍负责关闭外层 Feign Response。
     */
    @Override
    public Exception decode(final String methodKey, final Response response) {
        if (response.body() == null) {
            return FeignException.errorStatus(methodKey, response);
        }
        final byte[] bytes;
        try (final InputStream input = response.body().asInputStream()) {
            bytes = input.readNBytes(HttpRpcProtocol.MAX_ERROR_BYTES + 1);
        } catch (final IOException unreadable) {
            return HttpRpcErrorDecoder.transportFailure(methodKey, response, new byte[0], unreadable);
        }
        if (response.status() >= 300
                && bytes.length <= HttpRpcProtocol.MAX_ERROR_BYTES
                && HttpRpcErrorDecoder.matches(response, HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION)
                && HttpRpcErrorDecoder.matches(response, "Content-Type", HttpRpcProtocol.ERROR_CONTENT_TYPE)) {
            try {
                final Optional<ErrorCodeCarrier> error = this.readError(bytes);
                if (error.isPresent()) {
                    return new ErrorCodeException(error.orElseThrow());
                }
            } catch (final IOException malformed) {
                return HttpRpcErrorDecoder.transportFailure(methodKey, response, bytes, malformed);
            }
        }
        return HttpRpcErrorDecoder.transportFailure(methodKey, response, bytes, null);
    }

    /**
     * 校验文档结构与七个显式字符串字段，不让共享传播器使用旧协议的来源或可见性默认值。
     *
     * @return 版本、结构或字段无效时返回空；JSON 语法、重复字段或尾随文档错误由调用方保留诊断
     */
    private Optional<ErrorCodeCarrier> readError(final byte[] bytes) throws IOException {
        if (bytes.length == 0) {
            return Optional.empty();
        }
        final JsonNode document = this.mapper.readTree(bytes);
        if (document == null
                || !document.isObject()
                || !HttpRpcProtocol.VERSION.equals(document.path("version").textValue())) {
            return Optional.empty();
        }
        final JsonNode fields = document.path("error");
        if (!fields.isObject()) {
            return Optional.empty();
        }
        final Map<String, String> values = new LinkedHashMap<>();
        for (final String name : HttpRpcErrorDecoder.FIELDS) {
            final JsonNode value = fields.path(name);
            if (!value.isTextual()) {
                return Optional.empty();
            }
            values.put(name, value.asText());
        }
        return this.propagator.extract(name -> values.getOrDefault(name, ""));
    }

    /** 从已消费的有界正文构造传输异常，避免再次访问关闭的响应流，并保留可用的失败原因。 */
    private static FeignException transportFailure(
            final String methodKey, final Response response, final byte[] body, final @Nullable IOException cause) {
        final FeignException failure = FeignException.errorStatus(
                methodKey, response.toBuilder().body(body).build());
        if (cause != null) {
            failure.addSuppressed(cause);
        }
        return failure;
    }

    /** 响应头按大小写无关名称读取，协议标记必须唯一；媒体类型允许附带参数。 */
    private static boolean matches(final Response response, final String name, final String expected) {
        final List<String> values = response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .toList();
        if (values.size() != 1) {
            return false;
        }
        if ("Content-Type".equalsIgnoreCase(name)) {
            final String mediaType = values.getFirst().split(";", 2)[0].trim();
            return expected.equalsIgnoreCase(mediaType);
        }
        return expected.equals(values.getFirst());
    }
}
