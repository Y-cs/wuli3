package com.kjs.wuli3.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.web.internal.error.WebErrorResponseMapper;
import com.kjs.wuli3.web.internal.handler.DefaultWebErrorStatusResolver;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

/**
 * 验证有效 HTTP 错误状态与 Web 错误码的双向映射及责任覆盖。
 *
 * @author 国杨 create on 2026/11/3 10:46
 */
class WebErrorStatusMappingTest {

    private final DefaultWebErrorStatusResolver resolver = new DefaultWebErrorStatusResolver();

    /** 框架状态与业务错误之间往返时保留具体语义，五百系列默认属于服务端责任。 */
    @ParameterizedTest
    @MethodSource("knownErrors")
    void knownProtocolErrorsRoundTrip(final int value, final String name) {
        final HttpStatus status = HttpStatus.valueOf(value);
        final var code = WebErrorResponseMapper.responseCode(status);
        assertThat(code.getName()).isEqualTo(name);
        final ErrorCodeException error = new ErrorCodeException(code);
        assertThat(this.resolver.resolve(error, code)).isEqualTo(status);
        assertThat(error.getOrigin()).isEqualTo(status.is5xxServerError() ? ErrorOrigin.SERVER : ErrorOrigin.CALLER);
        if (status.is5xxServerError()) {
            assertThat(error.getSeverity()).isEqualTo(ErrorSeverity.WARNING);
        }
    }

    /** 责任被显式改为另一方时，不能继续使用相反责任的协议状态。 */
    @ParameterizedTest
    @MethodSource("knownErrors")
    void responsibilityOverrideWins(final int value, final String name) {
        final WebErrors code = WebErrors.valueOf(name);
        final boolean serverError = value >= 500;
        final ErrorCodeException error =
                new ErrorCodeException(code).withOrigin(serverError ? ErrorOrigin.CALLER : ErrorOrigin.SERVER);
        assertThat(this.resolver.resolve(error, code))
                .isEqualTo(serverError ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 兼容旧状态 SPI 的候选码输入，未知参数异常不再默认属于请求错误。 */
    @ParameterizedTest
    @MethodSource("knownErrors")
    void knownCodeCanSupplyFallback(final int value, final String name) {
        final WebErrors code = WebErrors.valueOf(name);
        assertThat(this.resolver.resolve(new RuntimeException(), code)).isEqualTo(HttpStatus.valueOf(value));
        assertThat(this.resolver.resolve(new IllegalArgumentException(), WebErrors.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Spring 的历史别名共享数值状态，不能产生不同的公开错误码。 */
    @Test
    void historicalAliasesUseTheSameCode() {
        for (final HttpStatus status : HttpStatus.values()) {
            if (status.value() == 413) {
                assertThat(WebErrorResponseMapper.responseCode(status)).isEqualTo(WebErrors.PAYLOAD_TOO_LARGE);
            } else if (status.value() == 414) {
                assertThat(WebErrorResponseMapper.responseCode(status).getName())
                        .isEqualTo("URI_TOO_LONG");
            }
        }
    }

    /** 保留未使用、未分配或已废止状态的类别兜底，不将其固化为新的公共错误码。 */
    @ParameterizedTest
    @CsvSource({"418, BAD_REQUEST", "419, BAD_REQUEST", "420, BAD_REQUEST", "509, INTERNAL_ERROR", "510, INTERNAL_ERROR"
    })
    void legacyStatusesUseCategoryFallback(final int value, final WebErrors expected) {
        assertThat(WebErrorResponseMapper.responseCode(HttpStatus.valueOf(value)))
                .isEqualTo(expected);
    }

    /** 成功标识不能作为异常成功返回，内部专用错误仍按责任归类。 */
    @Test
    void nonProtocolCodesKeepTheirFallbackSemantics() {
        assertThat(this.resolver.resolve(new ErrorCodeException(WebErrors.SUCCESS), WebErrors.SUCCESS))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(this.resolver.resolve(
                        new ErrorCodeException(WebErrors.SERVICE_CODE_NOT_FOUND), WebErrors.SERVICE_CODE_NOT_FOUND))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 固定有效协议清单，避免扩大枚举时遗漏反向映射或把过时名称重新暴露为公共契约。 */
    private static Stream<Arguments> knownErrors() {
        return Stream.of(
                Arguments.of(400, "BAD_REQUEST"),
                Arguments.of(401, "UNAUTHORIZED"),
                Arguments.of(402, "PAYMENT_REQUIRED"),
                Arguments.of(403, "FORBIDDEN"),
                Arguments.of(404, "NOT_FOUND"),
                Arguments.of(405, "METHOD_NOT_ALLOWED"),
                Arguments.of(406, "NOT_ACCEPTABLE"),
                Arguments.of(407, "PROXY_AUTHENTICATION_REQUIRED"),
                Arguments.of(408, "REQUEST_TIMEOUT"),
                Arguments.of(409, "CONFLICT"),
                Arguments.of(410, "GONE"),
                Arguments.of(411, "LENGTH_REQUIRED"),
                Arguments.of(412, "PRECONDITION_FAILED"),
                Arguments.of(413, "PAYLOAD_TOO_LARGE"),
                Arguments.of(414, "URI_TOO_LONG"),
                Arguments.of(415, "UNSUPPORTED_MEDIA_TYPE"),
                Arguments.of(416, "REQUESTED_RANGE_NOT_SATISFIABLE"),
                Arguments.of(417, "EXPECTATION_FAILED"),
                Arguments.of(421, "MISDIRECTED_REQUEST"),
                Arguments.of(422, "UNPROCESSABLE_ENTITY"),
                Arguments.of(423, "LOCKED"),
                Arguments.of(424, "FAILED_DEPENDENCY"),
                Arguments.of(425, "TOO_EARLY"),
                Arguments.of(426, "UPGRADE_REQUIRED"),
                Arguments.of(428, "PRECONDITION_REQUIRED"),
                Arguments.of(429, "TOO_MANY_REQUESTS"),
                Arguments.of(431, "REQUEST_HEADER_FIELDS_TOO_LARGE"),
                Arguments.of(451, "UNAVAILABLE_FOR_LEGAL_REASONS"),
                Arguments.of(500, "INTERNAL_ERROR"),
                Arguments.of(501, "NOT_IMPLEMENTED"),
                Arguments.of(502, "BAD_GATEWAY"),
                Arguments.of(503, "SERVICE_UNAVAILABLE"),
                Arguments.of(504, "GATEWAY_TIMEOUT"),
                Arguments.of(505, "HTTP_VERSION_NOT_SUPPORTED"),
                Arguments.of(506, "VARIANT_ALSO_NEGOTIATES"),
                Arguments.of(507, "INSUFFICIENT_STORAGE"),
                Arguments.of(508, "LOOP_DETECTED"),
                Arguments.of(511, "NETWORK_AUTHENTICATION_REQUIRED"));
    }
}
