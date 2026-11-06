package com.kjs.wuli3.openfeign.service.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 验证错误载荷保留完整语义，并拒绝损坏或不兼容的协议。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
class HttpRpcProtocolCompatibilityTest {
    private final HttpRpcResponses responses = new HttpRpcResponses("provider");
    private final HttpRpcErrorDecoder decoder = new HttpRpcErrorDecoder();

    /** 两端保持明确的线协议约定，避免模块收缩后常量静默漂移。 */
    @Test
    void agreesOnVersionMediaTypeAndLimits() {
        assertThat(HttpRpcProtocol.VERSION_HEADER).isEqualTo("X-Wuli3-Rpc-Version");
        assertThat(com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol.VERSION_HEADER)
                .isEqualTo(HttpRpcProtocol.VERSION_HEADER);
        assertThat(HttpRpcProtocol.VERSION).isEqualTo("1");
        assertThat(com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol.VERSION)
                .isEqualTo(HttpRpcProtocol.VERSION);
        assertThat(HttpRpcProtocol.ERROR_CONTENT_TYPE).isEqualTo("application/vnd.wuli3.rpc-error+json");
        assertThat(com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol.ERROR_CONTENT_TYPE)
                .isEqualTo(HttpRpcProtocol.ERROR_CONTENT_TYPE);
        assertThat(HttpRpcProtocol.MAX_ERROR_BYTES).isEqualTo(65536);
        assertThat(com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol.MAX_ERROR_BYTES)
                .isEqualTo(HttpRpcProtocol.MAX_ERROR_BYTES);
        assertThat(HttpRpcProtocol.MAX_HEADER_LENGTH).isEqualTo(4096);
        assertThat(com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol.MAX_HEADER_LENGTH)
                .isEqualTo(HttpRpcProtocol.MAX_HEADER_LENGTH);
    }

    /** 服务端写出的完整错误由独立客户端实现还原，包含所有可见性。 */
    @ParameterizedTest
    @EnumSource(ErrorVisibility.class)
    void preservesAllErrorSemantics(final ErrorVisibility visibility) throws IOException {
        final ErrorCodeCarrier error = new ErrorCodeCarrier(
                "original", "code", "诊断消息", ErrorOrigin.SERVER, ErrorSeverity.CRITICAL, "source", visibility);
        final MockHttpServletResponse output = this.response(error);
        assertThat(this.decode(output, output.getContentAsByteArray()))
                .isInstanceOfSatisfying(
                        ErrorCodeException.class,
                        decoded -> assertThat(decoded.asRemoteError()).contains(error));
        assertThat(output.getContentAsString()).doesNotContain("errorType", "@class");
    }

    /** 不兼容或损坏的文档不得还原为业务错误。 */
    @Test
    void rejectsUnsupportedIncompleteAndOversizedDocuments() throws IOException {
        assertThat(this.read("{\"version\":\"2\",\"error\":{}}")).isInstanceOf(FeignException.class);
        assertThat(this.read("{\"version\":\"1\",\"error\":{}}")).isInstanceOf(FeignException.class);
        assertThat(this.read(" ".repeat(HttpRpcProtocol.MAX_ERROR_BYTES + 1))).isInstanceOf(FeignException.class);
        for (final String malformed : java.util.List.of("{", "{} {}")) {
            final Exception failure = this.read(malformed);
            assertThat(failure).isInstanceOf(FeignException.class);
            assertThat(failure.getSuppressed())
                    .hasOnlyElementsOfType(IOException.class)
                    .hasSize(1);
        }
    }

    /** 当前版本要求显式字段，不从缺失信息推测来源或可见性。 */
    @Test
    void requiresVisibilityAndSourceInsteadOfLegacyDefaults() throws IOException {
        final ErrorCodeCarrier error = new ErrorCodeCarrier(
                "original",
                "code",
                "message",
                ErrorOrigin.SERVER,
                ErrorSeverity.CRITICAL,
                "source",
                ErrorVisibility.PUBLIC);
        final MockHttpServletResponse output = this.response(error);
        final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (final String field : java.util.List.of("X-Wuli3-Error-Visibility", "X-Wuli3-Error-Source-Service")) {
            final com.fasterxml.jackson.databind.JsonNode document = mapper.readTree(output.getContentAsByteArray());
            ((com.fasterxml.jackson.databind.node.ObjectNode) document.path("error")).remove(field);
            assertThat(this.decode(output, mapper.writeValueAsBytes(document))).isInstanceOf(FeignException.class);
        }
        final Exception duplicate = this.read("{\"version\":\"1\",\"version\":\"2\"}");
        assertThat(duplicate).isInstanceOf(FeignException.class);
        assertThat(duplicate.getSuppressed())
                .hasOnlyElementsOfType(IOException.class)
                .hasSize(1);
    }

    /** 错误输出经过响应边界生成，并由独立客户端解码。 */
    private MockHttpServletResponse response(final ErrorCodeCarrier error) {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(this.responses.resolveException(
                        new MockHttpServletRequest(), response, null, new ErrorCodeException(error)))
                .isNotNull();
        return response;
    }

    /** 仅替换真实错误响应的正文，保留服务端产生的状态及协议标记。 */
    private Exception read(final String json) throws IOException {
        final MockHttpServletResponse output = new MockHttpServletResponse();
        this.responses.status(output, 500);
        return this.decode(output, json.getBytes(StandardCharsets.UTF_8));
    }

    /** 在完整客户端边界验证响应，防止仅测试正文时遗漏两端响应头不兼容。 */
    private Exception decode(final MockHttpServletResponse output, final byte[] body) {
        final Map<String, Collection<String>> headers = new LinkedHashMap<>();
        output.getHeaderNames().forEach(name -> headers.put(name, output.getHeaders(name)));
        try (final Response response = Response.builder()
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://internal",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate()))
                .status(output.getStatus())
                .headers(headers)
                .body(body)
                .build()) {
            return this.decoder.decode("sample", response);
        }
    }
}
