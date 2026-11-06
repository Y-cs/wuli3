package com.kjs.wuli3.openfeign.service.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 验证错误响应在编码完成后才替换业务输出，超限时保持完整的安全协议文档。
 *
 * @author 国杨 create on 2026/11/5 16:00
 */
class HttpRpcResponsesTest {

    /** 超限错误必须清空未提交的业务缓冲，不能混合正文或泄露原始消息。 */
    @Test
    void replacesBufferedOutputWithSafeErrorWhenMessageExceedsLimit() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream().write("partial-business-output".getBytes(StandardCharsets.UTF_8));
        final ErrorCodeException exception = new ErrorCodeException(
                CommonErrors.ILLEGAL_ARGUMENT, "secret-".repeat(HttpRpcProtocol.MAX_ERROR_BYTES));
        final HttpRpcResponses responses = new HttpRpcResponses("provider");
        assertThat(responses.resolveException(new MockHttpServletRequest(), response, null, exception))
                .isNotNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).hasSizeLessThanOrEqualTo(HttpRpcProtocol.MAX_ERROR_BYTES);
        assertThat(response.getContentAsString()).doesNotContain("partial-business-output", "secret-");
        final Map<String, Collection<String>> headers = new LinkedHashMap<>();
        response.getHeaderNames().forEach(name -> headers.put(name, response.getHeaders(name)));
        try (final Response transport = Response.builder()
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://internal",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate()))
                .status(response.getStatus())
                .headers(headers)
                .body(response.getContentAsByteArray())
                .build()) {
            assertThat(new HttpRpcErrorDecoder().decode("sample", transport))
                    .isInstanceOfSatisfying(
                            ErrorCodeException.class,
                            error -> assertThat(
                                            error.asRemoteError().orElseThrow().code())
                                    .isEqualTo("PROVIDER.HTTP_RPC.INTERNAL_ERROR"));
        }
    }

    /** 服务标识等内部数据使安全文档也超限时，保留失败且不写出不完整协议。 */
    @Test
    void doesNotWriteAnOversizedFallbackDocument() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream().write("existing-buffer".getBytes(StandardCharsets.UTF_8));
        final HttpRpcResponses responses = new HttpRpcResponses("s".repeat(HttpRpcProtocol.MAX_ERROR_BYTES));
        final ErrorCodeException exception = new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "message");
        assertThat(responses.resolveException(new MockHttpServletRequest(), response, null, exception))
                .isNull();
        assertThat(response.getContentAsString()).isEqualTo("existing-buffer");
        assertThat(response.getHeader(HttpRpcProtocol.VERSION_HEADER)).isNull();
    }
}
