package com.kjs.wuli3.openfeign.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 Feign 错误响应头到远程错误语义的还原。
 *
 * @author lipt create on 2026/10/29 18:21
 */
class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    /** 完整错误头转换为携带原始来源的 ErrorCodeException。 */
    @Test
    void decodesCompleteErrorHeaders() {
        final Map<String, Collection<String>> headers = new LinkedHashMap<>();
        new ErrorCodePropagator()
                .inject(
                        new ErrorCodeCarrier(
                                "ORDER.ORDER.NOT_FOUND",
                                "ORDER.ORDER.NOT_FOUND",
                                "denied",
                                ErrorOrigin.CALLER,
                                ErrorSeverity.NORMAL,
                                "order-service",
                                ErrorVisibility.INTERNAL),
                        (name, value) -> headers.put(name, List.of(value)));

        final Exception decoded = this.decoder.decode("OrderClient#find", FeignErrorDecoderTest.response(headers));

        assertThat(decoded).isInstanceOf(ErrorCodeException.class).hasCauseInstanceOf(FeignException.class);
        final ErrorCodeCarrier carrier =
                ((ErrorCodeException) decoded).asRemoteError().orElseThrow();
        assertThat(carrier.code()).isEqualTo("ORDER.ORDER.NOT_FOUND");
        assertThat(carrier.message()).isEqualTo("denied");
        assertThat(carrier.origin()).isEqualTo(ErrorOrigin.CALLER);
        assertThat(carrier.severity()).isEqualTo(ErrorSeverity.NORMAL);
        assertThat(carrier.sourceService()).isEqualTo("order-service");
        assertThat(carrier.visibility()).isEqualTo(ErrorVisibility.INTERNAL);
    }

    /** HTTP 头名称不区分大小写，小写写法同样能够还原协议。 */
    @Test
    void decodesErrorHeadersIgnoringCase() {
        final Map<String, Collection<String>> headers = new LinkedHashMap<>();
        new ErrorCodePropagator()
                .inject(
                        new ErrorCodeCarrier(
                                "ORDER.ORDER.NOT_FOUND",
                                "ORDER.ORDER.NOT_FOUND",
                                "denied",
                                ErrorOrigin.CALLER,
                                ErrorSeverity.NORMAL,
                                "order-service",
                                ErrorVisibility.PUBLIC),
                        (name, value) -> headers.put(name.toLowerCase(), List.of(value)));

        assertThat(this.decoder.decode("OrderClient#find", FeignErrorDecoderTest.response(headers)))
                .isInstanceOf(ErrorCodeException.class);
    }

    /** 错误头缺失或非法时保留 Feign 原始异常。 */
    @Test
    void fallsBackToFeignExceptionWhenHeadersAreNotDecodable() {
        final Map<String, Collection<String>> missing = Map.of();
        final Map<String, Collection<String>> invalid = Map.of(
                ErrorCodePropagator.CODE, List.of("ORDER.ORDER.NOT_FOUND"),
                ErrorCodePropagator.ORIGINAL_CODE, List.of("ORDER.ORDER.NOT_FOUND"),
                ErrorCodePropagator.MESSAGE, List.of("denied"),
                ErrorCodePropagator.ORIGIN, List.of("CALLER"),
                ErrorCodePropagator.SEVERITY, List.of("NORMAL"),
                ErrorCodePropagator.SOURCE_SERVICE, List.of("order-service"),
                ErrorCodePropagator.VISIBILITY, List.of("UNKNOWN"));

        assertThat(this.decoder.decode("OrderClient#find", FeignErrorDecoderTest.response(missing)))
                .isInstanceOf(FeignException.class);
        assertThat(this.decoder.decode("OrderClient#find", FeignErrorDecoderTest.response(invalid)))
                .isInstanceOf(FeignException.class);
    }

    private static Response response(final Map<String, Collection<String>> headers) {
        return Response.builder()
                .status(400)
                .reason("Bad Request")
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://order-service/orders",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8))
                .headers(headers)
                .build();
    }
}
