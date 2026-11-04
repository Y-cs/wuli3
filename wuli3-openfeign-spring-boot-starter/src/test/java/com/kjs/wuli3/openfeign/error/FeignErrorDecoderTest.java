package com.kjs.wuli3.openfeign.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * 验证 Web 错误响应体到远程错误语义的还原。
 *
 * @author lipt create on 2026/11/4 11:20
 */
class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder(new ObjectMapper());

    /** 统一响应体中的展示码和消息转换为 ErrorCodeException，并保留 Feign 异常作为 cause。 */
    @Test
    void decodesDisplayedErrorFromBody() {
        final Response response = FeignErrorDecoderTest.response(
                400,
                "application/json",
                "{\"code\":\"ORDER.NOT_FOUND\",\"message\":\"订单不存在\","
                        + "\"timestamp\":1,\"requestId\":\"r-1\",\"data\":null}");

        final Exception decoded = this.decoder.decode("OrderClient#find", response);

        assertThat(decoded).isInstanceOf(ErrorCodeException.class).hasCauseInstanceOf(FeignException.BadRequest.class);
        assertThat(decoded).hasMessage("订单不存在");
        final ErrorCodeCarrier carrier =
                ((ErrorCodeException) decoded).asRemoteError().orElseThrow();
        assertThat(carrier.code()).isEqualTo("ORDER.NOT_FOUND");
        assertThat(carrier.originalCode()).isEqualTo("ORDER.NOT_FOUND");
        assertThat(carrier.message()).isEqualTo("订单不存在");
        assertThat(carrier.origin()).isEqualTo(ErrorOrigin.CALLER);
        assertThat(carrier.severity()).isEqualTo(ErrorSeverity.NORMAL);
        assertThat(carrier.sourceService()).isEmpty();
        assertThat(carrier.visibility()).isEqualTo(ErrorVisibility.PUBLIC);
        assertThat(Objects.requireNonNull(decoded.getCause()).getMessage()).contains("ORDER.NOT_FOUND");
    }

    /** 5xx 归属服务方，供本地状态映射使用。 */
    @Test
    void derivesServerOriginFromErrorStatus() {
        final Response response = FeignErrorDecoderTest.response(
                500, "application/json", "{\"code\":\"SYSTEM.INTERNAL_ERROR\",\"message\":\"内部错误\"}");

        final ErrorCodeCarrier carrier = ((ErrorCodeException) this.decoder.decode("OrderClient#find", response))
                .asRemoteError()
                .orElseThrow();

        assertThat(carrier.origin()).isEqualTo(ErrorOrigin.SERVER);
    }

    /** 非 JSON 响应体交给 Feign 默认解码，不解析二进制或 HTML 错误页。 */
    @Test
    void keepsFeignExceptionForNonJsonBody() {
        final Response response = FeignErrorDecoderTest.response(500, "text/html", "<html><body>error</body></html>");

        assertThat(this.decoder.decode("OrderClient#find", response)).isInstanceOf(FeignException.class);
    }

    /** 响应体缺少 code、使用成功码或不是合法 JSON 时保留 Feign 原始异常。 */
    @Test
    void keepsFeignExceptionWhenBodyIsNotAnErrorPayload() {
        for (final String body :
                List.of("{\"message\":\"missing code\"}", "{\"code\":\"0\",\"message\":\"\"}", "{not json")) {
            final Response response = FeignErrorDecoderTest.response(400, "application/json", body);
            assertThat(this.decoder.decode("OrderClient#find", response)).isInstanceOf(FeignException.class);
        }
    }

    /** 无响应体时保持 Feign 默认解码。 */
    @Test
    void keepsFeignExceptionWithoutBody() {
        assertThat(this.decoder.decode("OrderClient#find", FeignErrorDecoderTest.responseWithoutBody()))
                .isInstanceOf(FeignException.class);
    }

    private static Response response(final int status, final String contentType, final String body) {
        return FeignErrorDecoderTest.request(status)
                .headers(Map.of("content-type", List.of(contentType)))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private static Response responseWithoutBody() {
        return FeignErrorDecoderTest.request(503).build();
    }

    private static Response.Builder request(final int status) {
        return Response.builder()
                .status(status)
                .reason("failure")
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://order-service/orders",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8));
    }
}
