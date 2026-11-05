package com.kjs.wuli3.openfeign.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 验证显式 Feign 配置的上下文传递、独立序列化和错误协议防绕过行为。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
class HttpRpcClientTest {
    @ParameterizedTest
    @EnumSource(ErrorVisibility.class)
    void restoresErrorsBeforeVoidAndDismiss404Branches(final ErrorVisibility visibility) throws IOException {
        final ErrorCodeCarrier error = new ErrorCodeCarrier(
                "original", "code", "内部消息", ErrorOrigin.CALLER, ErrorSeverity.WARNING, "origin-service", visibility);
        final byte[] document = HttpRpcClientTest.errorDocument(error);
        final Api api = Feign.builder()
                .addCapability(new HttpRpcCapability(new HttpRpcErrorDecoder()))
                .dismiss404()
                .client((request, options) -> HttpRpcClientTest.response(request, 404, document, true))
                .target(Api.class, "http://internal");
        assertThatThrownBy(api::nothing)
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        exception -> assertThat(exception.asRemoteError()).contains(error));
        assertThatThrownBy(api::value).isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void malformedMissingAndOversizedProtocolStayTransportFailures() {
        for (final byte[] bytes : List.of(
                "{".getBytes(StandardCharsets.UTF_8),
                "{\"version\":\"2\"}".getBytes(StandardCharsets.UTF_8),
                new byte[HttpRpcProtocol.MAX_ERROR_BYTES + 1])) {
            final Api api = Feign.builder()
                    .addCapability(new HttpRpcCapability(new HttpRpcErrorDecoder()))
                    .client((request, options) -> HttpRpcClientTest.response(request, 500, bytes, true))
                    .target(Api.class, "http://internal");
            assertThatThrownBy(api::nothing).isInstanceOf(FeignException.class);
        }
        final Api unmarked = Feign.builder()
                .addCapability(new HttpRpcCapability(new HttpRpcErrorDecoder()))
                .client((request, options) -> HttpRpcClientTest.response(request, 500, new byte[0], false))
                .target(Api.class, "http://internal");
        assertThatThrownBy(unmarked::nothing).isInstanceOf(FeignException.class);
    }

    @Test
    void independentJsonPreservesGenericTypesAndDisplayAnnotatedValues() throws Exception {
        final Wuli3FeignConfiguration configuration = new Wuli3FeignConfiguration();
        final Data source = new Data("13812345678", "private/file.png");
        final RequestTemplate template = new RequestTemplate();
        configuration.httpRpcEncoder().encode(source, Data.class, template);
        assertThat(new String(template.body(), StandardCharsets.UTF_8)).contains("13812345678", "private/file.png");
        final Request request = Request.create(
                Request.HttpMethod.GET,
                "http://internal",
                Map.of(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate());
        final Response response = HttpRpcClientTest.response(
                request,
                200,
                ("[" + new String(template.body(), StandardCharsets.UTF_8) + "]").getBytes(StandardCharsets.UTF_8),
                false);
        final Object result = configuration
                .httpRpcDecoder()
                .decode(response, new com.fasterxml.jackson.core.type.TypeReference<List<Data>>() {}.getType());
        assertThat(result).isEqualTo(List.of(source));
    }

    @Test
    void rejectsRawResponseReturnType() {
        assertThatThrownBy(() -> Feign.builder()
                        .addCapability(new HttpRpcCapability(new HttpRpcErrorDecoder()))
                        .target(RawApi.class, "http://internal"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 独立构造协议样本，客户端测试不依赖提供端实现。 */
    private static byte[] errorDocument(final ErrorCodeCarrier error) throws IOException {
        final Map<String, String> fields = new java.util.LinkedHashMap<>();
        new com.kjs.wuli3.core.error.propagation.ErrorCodePropagator().inject(error, fields::put);
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsBytes(Map.of("version", "1", "error", fields));
    }

    private static Response response(final Request request, final int status, final byte[] body, final boolean marked) {
        return Response.builder()
                .request(request)
                .status(status)
                .headers(
                        marked
                                ? Map.of(
                                        HttpRpcProtocol.VERSION_HEADER,
                                        List.of(HttpRpcProtocol.VERSION),
                                        "Content-Type",
                                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE))
                                : Map.of())
                .body(body)
                .build();
    }

    record Data(
            @com.kjs.wuli3.json.datatype.desensitization.Desensitized(type = "phone")
            String phone,

            @com.kjs.wuli3.json.datatype.resource.ResourcePath
            String path) {}

    interface Api {
        @RequestLine("GET /nothing")
        void nothing();

        @RequestLine("GET /value")
        String value();
    }

    interface RawApi {
        @RequestLine("GET /raw")
        Response raw();
    }
}
