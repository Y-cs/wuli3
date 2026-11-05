package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.web.error.ErrorAlertContext;
import com.kjs.wuli3.web.error.ErrorAlertNotifier;
import com.kjs.wuli3.web.error.WebErrorMapper;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import com.kjs.wuli3.web.error.WebErrors;
import com.kjs.wuli3.web.internal.advice.ApiResponseFactory;
import com.kjs.wuli3.web.internal.handler.DefaultWebErrorStatusResolver;
import com.kjs.wuli3.web.internal.handler.WebExceptionHandler;
import com.kjs.wuli3.web.response.ApiResponse;
import com.kjs.wuli3.web.response.NativeResponse;
import com.kjs.wuli3.web.response.NativeResponseMode;
import com.kjs.wuli3.web.response.WebResponseProperties;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 验证 Web 归类与整个错误响应的脱敏边界。
 *
 * @author 国杨 create on 2026/9/29 19:04
 */
class WebErrorBoundaryTest {

    /** 返回值校验失败属于服务端契约错误，且不会返回字段详情。 */
    @Test
    void returnValueViolationIsServerFailure() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator()
                    .forExecutables()
                    .validateReturnValue(
                            new ValidatedService(), ValidatedService.class.getDeclaredMethod("result"), null);
            final var response = WebErrorBoundaryTest.handler(List.of())
                    .handleException(new ConstraintViolationException(violations), new MockHttpServletRequest());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(((ApiResponse<?>) Objects.requireNonNull(response.getBody())).data())
                    .isNull();
        }
    }

    /** 状态覆盖只能改变协议结果，不能把返回值校验变成可公开的请求详情。 */
    @ParameterizedTest
    @CsvSource({"400", "429"})
    void statusOverrideCannotExposeReturnValueViolation(final int status) throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator()
                    .forExecutables()
                    .validateReturnValue(
                            new ValidatedService(), ValidatedService.class.getDeclaredMethod("result"), null);
            final var response = WebErrorBoundaryTest.handler(
                            List.of(), (source, code) -> HttpStatus.valueOf(status), List.of())
                    .handleException(new ConstraintViolationException(violations), new MockHttpServletRequest());
            assertThat(response.getStatusCode().value()).isEqualTo(status);
            final ApiResponse<?> body = (ApiResponse<?>) Objects.requireNonNull(response.getBody());
            assertThat(body.data()).isNull();
            assertThat(body.toString()).doesNotContain("secret return constraint", "<return value>");
        }
    }

    /** 已识别的请求约束在最终仍为 4xx 时保留详情，状态 SPI 不改变其输入性质。 */
    @Test
    void statusOverridePreservesRequestValidationDetails() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator()
                    .forExecutables()
                    .validateParameters(
                            new ValidatedService(),
                            ValidatedService.class.getDeclaredMethod("accept", String.class),
                            new Object[] {null});
            final var response = WebErrorBoundaryTest.handler(
                            List.of(), (source, code) -> HttpStatus.TOO_MANY_REQUESTS, List.of())
                    .handleException(new ConstraintViolationException(violations), new MockHttpServletRequest());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            final ApiResponse<?> body = (ApiResponse<?>) Objects.requireNonNull(response.getBody());
            assertThat(body.data()).isNotNull();
            assertThat(Objects.requireNonNull(body.data()).toString()).contains("required input");
        }
    }

    /** 尊重框架协议状态，并保留方法协商必需的响应头。 */
    @Test
    void preservesFrameworkStatusesAndProtocolHeaders() {
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        final MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(handler.handleException(new HttpMediaTypeNotAcceptableException("secret"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(handler.handleException(new MaxUploadSizeExceededException(100), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        final var result =
                handler.handleException(new HttpRequestMethodNotSupportedException("POST", List.of("GET")), request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getHeaders().getFirst("Allow")).isEqualTo("GET");
        assertThat(handler.handleException(
                                new ErrorCodeException(WebErrors.PAYLOAD_TOO_LARGE)
                                        .withOrigin(ErrorOrigin.SERVER)
                                        .withVisibility(ErrorVisibility.INTERNAL),
                                request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 透明包装不会丢失框架声明的状态和协议协商头，也不会泄露异常原因。 */
    @Test
    void preservesWrappedProtocolErrors() {
        final var result = WebErrorBoundaryTest.handler(List.of())
                .handleException(
                        new CompletionException(new HttpRequestMethodNotSupportedException("POST", List.of("GET"))),
                        new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getHeaders().getFirst("Allow")).isEqualTo("GET");
        assertThat(Objects.requireNonNull(result.getBody()).toString()).doesNotContain("POST");
    }

    /** 显式映射改变状态时，不复制原状态的协商头；诊断头始终不对外输出。 */
    @Test
    void protocolHeadersRespectFinalStatusAndWhitelist() {
        final ErrorResponseException error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        error.getHeaders().set("Retry-After", "60");
        error.getHeaders().set("X-Diagnostic", "secret");
        final var defaultResult = WebErrorBoundaryTest.handler(List.of())
                .handleException(new CompletionException(error), new MockHttpServletRequest());
        assertThat(defaultResult.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(defaultResult.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(defaultResult.getHeaders()).doesNotContainKey("X-Diagnostic");
        final var mappedResult = WebErrorBoundaryTest.handler(
                        List.of(failure -> new ErrorCodeException(WebErrors.FORBIDDEN)))
                .handleException(error, new MockHttpServletRequest());
        assertThat(mappedResult.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mappedResult.getHeaders()).doesNotContainKeys("Retry-After", "X-Diagnostic");
    }

    /** 注解的原因文本仍经过统一响应投影，不直接作为公开消息。 */
    @Test
    void responseStatusReasonDoesNotBypassVisibilityBoundary() {
        final var result = WebErrorBoundaryTest.handler(List.of())
                .handleException(new MissingResourceException(), new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).message())
                .isEqualTo("资源不存在");
        assertThat(result.getBody().toString()).doesNotContain("secret");
    }

    /** 非标准状态的回退在普通及原生响应模式下均不泄露框架详情。 */
    @Test
    void normalizesNonstandardStatusInBothResponseModes() throws Exception {
        final MockHttpServletRequest nativeRequest = new MockHttpServletRequest();
        nativeRequest.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new NativeController(), NativeController.class.getDeclaredMethod("endpoint")));
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        for (final MockHttpServletRequest request : List.of(new MockHttpServletRequest(), nativeRequest)) {
            final ProblemDetail unsafe = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(499), "secret detail");
            final var result = handler.handleException(
                    new ErrorResponseException(HttpStatusCode.valueOf(499), unsafe, null), request);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Objects.requireNonNull(result.getBody()).toString()).doesNotContain("secret");
            if (result.getBody() instanceof ProblemDetail problem) {
                assertThat(problem.getStatus()).isEqualTo(400);
            }
        }
    }

    /** 普通与原生响应使用相同的具体协议错误码，保留状态且不泄露框架详情。 */
    @ParameterizedTest
    @CsvSource({
        "405, METHOD_NOT_ALLOWED",
        "406, NOT_ACCEPTABLE",
        "408, REQUEST_TIMEOUT",
        "409, CONFLICT",
        "415, UNSUPPORTED_MEDIA_TYPE",
        "421, MISDIRECTED_REQUEST",
        "422, UNPROCESSABLE_ENTITY",
        "429, TOO_MANY_REQUESTS",
        "502, BAD_GATEWAY",
        "503, SERVICE_UNAVAILABLE",
        "504, GATEWAY_TIMEOUT"
    })
    void knownStatusesKeepSpecificCodesInBothModes(final int status, final String name) throws Exception {
        final MockHttpServletRequest nativeRequest = new MockHttpServletRequest();
        nativeRequest.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new NativeController(), NativeController.class.getDeclaredMethod("endpoint")));
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        for (final MockHttpServletRequest request : List.of(new MockHttpServletRequest(), nativeRequest)) {
            final ProblemDetail unsafe =
                    ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), "secret detail");
            unsafe.setProperty("stack", "secret stack");
            final var result = handler.handleException(
                    new ErrorResponseException(HttpStatus.valueOf(status), unsafe, null), request);
            assertThat(result.getStatusCode().value()).isEqualTo(status);
            if (result.getBody() instanceof ProblemDetail problem) {
                assertThat(problem.getStatus()).isEqualTo(status);
                assertThat(problem.getProperties())
                        .containsEntry("code", "WEB." + name)
                        .doesNotContainKey("stack");
                if (status == 421) {
                    assertThat(problem.getTitle()).isEqualTo("Misdirected Request");
                }
            } else {
                assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).code())
                        .isEqualTo("WEB." + name);
            }
            assertThat(Objects.requireNonNull(result.getBody()).toString()).doesNotContain("secret");
        }
    }

    /** 认证与授权错误保留 HTTP 状态语义，不能按调用方错误降为 400。 */
    @Test
    void mapsAuthenticationAndAuthorizationCodesToHttpStatuses() {
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        final MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(handler.handleException(new ErrorCodeException(WebErrors.UNAUTHORIZED), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleException(new ErrorCodeException(WebErrors.FORBIDDEN), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** 无明确请求边界的参数异常与未知包装均按内部故障处理。 */
    @Test
    void unclassifiedArgumentsAreServerFailures() {
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        final MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(handler.handleException(new NumberFormatException("secret"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        for (final Exception error : List.of(
                new NullPointerException("secret"),
                new IllegalStateException("secret"),
                new RuntimeException(new IllegalArgumentException("secret")))) {
            final var result = handler.handleException(error, request);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).message())
                    .isEqualTo("内部错误");
        }
    }

    /** 透明包装内的业务错误保留完整语义及运行时覆盖，普通包装不公开内部消息。 */
    @Test
    void wrappedBusinessErrorsPreserveBoundaryMetadata() {
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        for (final ErrorVisibility visibility : ErrorVisibility.values()) {
            final ErrorCodeException business = new ErrorCodeException(WebErrors.NOT_FOUND, "business message")
                    .withOrigin(ErrorOrigin.SERVER)
                    .withVisibility(visibility);
            final var expected = new ErrorResolver("").resolveBoundary(business);
            final var result = handler.handleException(new CompletionException(business), new MockHttpServletRequest());
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            final ApiResponse<?> body = (ApiResponse<?>) Objects.requireNonNull(result.getBody());
            assertThat(body.code()).isEqualTo(expected.code());
            assertThat(body.message()).isEqualTo(expected.message());
        }
        final var opaque = handler.handleException(
                new RuntimeException(new ErrorCodeException(WebErrors.NOT_FOUND, "secret business")),
                new MockHttpServletRequest());
        assertThat(opaque.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(Objects.requireNonNull(opaque.getBody()).toString()).doesNotContain("secret business");
    }

    /** 显式映射即使保持原 HTTP 状态，也不能继承原协议异常的头。 */
    @Test
    void explicitMappingFormsAProtocolHeaderBoundary() {
        final ErrorResponseException error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        error.getHeaders().set("Retry-After", "60");
        final var result = WebErrorBoundaryTest.handler(
                        List.of(failure -> new ErrorCodeException(WebErrors.TOO_MANY_REQUESTS)))
                .handleException(error, new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getHeaders()).doesNotContainKey("Retry-After");
    }

    /** 协议状态和头共用一次透明解包，外层注解不会从内层继承头。 */
    @Test
    void protocolDecisionUsesOneSourceAndOneUnwrap() {
        final CountingWrapper wrapper =
                new CountingWrapper(new HttpRequestMethodNotSupportedException("POST", List.of("GET")));
        final var result =
                WebErrorBoundaryTest.handler(List.of()).handleException(wrapper, new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getHeaders().getFirst("Allow")).isEqualTo("GET");
        assertThat(wrapper.causeReads.get()).isEqualTo(1);
        final ErrorResponseException inner = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        inner.getHeaders().set("Retry-After", "60");
        final var outer = WebErrorBoundaryTest.handler(List.of())
                .handleException(new DeclaredWrapper(inner), new MockHttpServletRequest());
        assertThat(outer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(outer.getHeaders()).doesNotContainKey("Retry-After");
    }

    /** 自定义状态 SPI 调用一次且得到实际候选码，变更状态后不携带原协议头。 */
    @Test
    void customStatusOverrideIsAppliedOnceAfterClassification() {
        final AtomicInteger calls = new AtomicInteger();
        final ErrorResponseException error = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        error.getHeaders().set("Retry-After", "60");
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(
                List.of(),
                (source, code) -> {
                    assertThat(source).isSameAs(error);
                    assertThat(code).isEqualTo(WebErrors.SERVICE_UNAVAILABLE);
                    calls.incrementAndGet();
                    return HttpStatus.TOO_MANY_REQUESTS;
                },
                List.of());
        final var result = handler.handleException(error, new MockHttpServletRequest());
        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getHeaders()).doesNotContainKey("Retry-After");
        assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).code())
                .isEqualTo("WEB.TOO_MANY_REQUESTS");
    }

    /** 数值相同的历史状态别名不构成协议变化，仍保留所选来源的协商头。 */
    @Test
    void statusAliasesDoNotDiscardProtocolHeaders() {
        for (final HttpStatus alias : HttpStatus.values()) {
            if (alias.value() != 413) {
                continue;
            }
            final ErrorResponseException error = new ErrorResponseException(HttpStatus.PAYLOAD_TOO_LARGE);
            error.getHeaders().set("Retry-After", "60");
            final var result = WebErrorBoundaryTest.handler(List.of(), (source, code) -> alias, List.of())
                    .handleException(error, new MockHttpServletRequest());
            assertThat(result.getStatusCode().value()).isEqualTo(413);
            assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        }
    }

    /** 状态扩展不能把错误变成成功或重定向，业务元数据不随状态覆盖改变。 */
    @ParameterizedTest
    @CsvSource({"200", "302"})
    void normalizesInvalidCustomStatus(final int status) {
        final WebExceptionHandler handler =
                WebErrorBoundaryTest.handler(List.of(), (source, code) -> HttpStatus.valueOf(status), List.of());
        final var result =
                handler.handleException(new ErrorCodeException(WebErrors.NOT_FOUND), new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).code())
                .isEqualTo("WEB.NOT_FOUND");
    }

    /** 透明包装保留远程严重度与责任，告警诊断仍指向最外层原始异常。 */
    @Test
    void wrappedRemoteMetadataStillControlsAlerts() {
        final List<ErrorAlertContext> alerts = new ArrayList<>();
        final ErrorCodeCarrier remote = new ErrorCodeCarrier(
                "REMOTE.ORIGINAL.FAILURE",
                "REMOTE.PUBLIC.FAILURE",
                "secret remote message",
                ErrorOrigin.SERVER,
                ErrorSeverity.CRITICAL,
                "remote-service",
                ErrorVisibility.INTERNAL);
        final ErrorCodeException business = new ErrorCodeException(remote).withOrigin(ErrorOrigin.CALLER);
        final CompletionException wrapper = new CompletionException(business);
        final var result = WebErrorBoundaryTest.handler(
                        List.of(), new DefaultWebErrorStatusResolver(), List.of(alerts::add))
                .handleException(wrapper, new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(result.getBody()).toString()).doesNotContain("secret remote message");
        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().error()).isSameAs(wrapper);
        final ErrorCodeCarrier visible = (ErrorCodeCarrier) alerts.getFirst().responseCode();
        assertThat(visible.origin()).isEqualTo(ErrorOrigin.CALLER);
        assertThat(visible.severity()).isEqualTo(ErrorSeverity.CRITICAL);
        assertThat(visible.originalCode()).isEqualTo(remote.originalCode());
        assertThat(visible.sourceService()).isEqualTo(remote.sourceService());
    }

    /** 显式映射同时控制责任与可见性，隐藏模式不输出校验字段。 */
    @Test
    void explicitMappingSuppressesValidationDetails() {
        for (final ErrorVisibility visibility :
                List.of(ErrorVisibility.INTERNAL, ErrorVisibility.CODE_ONLY, ErrorVisibility.MESSAGE_ONLY)) {
            final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of(error ->
                    new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "safe message").withVisibility(visibility)));
            final var result = handler.handleException(
                    new MissingServletRequestParameterException("secretField", "secretType"),
                    new MockHttpServletRequest());
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).data())
                    .isNull();
            assertThat(Objects.requireNonNull(result.getBody()).toString()).doesNotContain("secretField", "secretType");
        }
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(
                List.of(error -> new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "public server message")
                        .withOrigin(ErrorOrigin.SERVER)
                        .withVisibility(ErrorVisibility.PUBLIC)));
        final var result = handler.handleException(new IllegalArgumentException(), new MockHttpServletRequest());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).message())
                .isEqualTo("public server message");
    }

    /** 原生 ProblemDetail 的任意详情、类型及扩展属性不能绕过边界策略。 */
    @Test
    void rebuildsProblemDetailAfterVisibilityFiltering() throws Exception {
        final ProblemDetail unsafe = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "secret detail");
        unsafe.setProperty("stack", "secret stack");
        final ErrorResponseException error = new ErrorResponseException(HttpStatus.BAD_REQUEST, unsafe, null);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new NativeController(), NativeController.class.getDeclaredMethod("endpoint")));
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(
                List.of(failure -> new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "secret message")
                        .withVisibility(ErrorVisibility.INTERNAL)));
        final var result = handler.handleException(error, request);
        final ProblemDetail body = (ProblemDetail) Objects.requireNonNull(result.getBody());
        assertThat(body.getDetail()).isEqualTo("请求未被接受");
        assertThat(body.getProperties()).doesNotContainKey("stack");
        assertThat(body.toString()).doesNotContain("secret");
        final var defaultResult = WebErrorBoundaryTest.handler(List.of()).handleException(error, request);
        assertThat(Objects.requireNonNull(defaultResult.getBody()).toString()).doesNotContain("secret");
    }

    /** 直接创建错误响应也不能通过 data 绕过策略。 */
    @Test
    void failureFactorySuppressesStructuredDetails() {
        final ErrorResolver resolver = new ErrorResolver("");
        final var carrier = resolver.resolveBoundary(
                new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT).withVisibility(ErrorVisibility.INTERNAL));
        assertThat(ApiResponse.failure(carrier, "request", "secret").data()).isNull();
    }

    /** 错误响应只保留掩码后的响应体，不输出内部错误传播协议头。 */
    @Test
    void doesNotExposeErrorPropagationHeaders() {
        final WebResponseProperties properties = new WebResponseProperties();
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of(), properties);
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final ErrorCodeException error = new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "secret")
                .withVisibility(ErrorVisibility.INTERNAL);

        final var result = handler.handleException(error, request);
        assertThat(result.getHeaders().keySet()).noneMatch(name -> name.startsWith("X-Wuli3-Error-"));
        assertThat(((ApiResponse<?>) Objects.requireNonNull(result.getBody())).message())
                .isEqualTo("请求未被接受");
    }

    /** 创建使用真实 core 投影和 HTTP 分类的处理器。 */
    private static WebExceptionHandler handler(final List<WebErrorMapper> mappers) {
        return WebErrorBoundaryTest.handler(mappers, new DefaultWebErrorStatusResolver(), List.of());
    }

    /** 创建带自定义状态和告警出口的处理器，用于验证 SPI 边界。 */
    private static WebExceptionHandler handler(
            final List<WebErrorMapper> mappers,
            final WebErrorStatusResolver statusResolver,
            final List<ErrorAlertNotifier> notifiers) {
        return WebErrorBoundaryTest.handler(mappers, new WebResponseProperties(), statusResolver, notifiers);
    }

    /** 创建使用指定响应配置的处理器。 */
    private static WebExceptionHandler handler(
            final List<WebErrorMapper> mappers, final WebResponseProperties properties) {
        return WebErrorBoundaryTest.handler(mappers, properties, new DefaultWebErrorStatusResolver(), List.of());
    }

    /** 组装处理器，响应配置、状态 SPI 与告警出口均可指定。 */
    private static WebExceptionHandler handler(
            final List<WebErrorMapper> mappers,
            final WebResponseProperties properties,
            final WebErrorStatusResolver statusResolver,
            final List<ErrorAlertNotifier> notifiers) {
        final InvocationContextAccessor accessor = mock(InvocationContextAccessor.class);
        return new WebExceptionHandler(
                new ApiResponseFactory(accessor, new ErrorResolver("")),
                accessor,
                properties,
                notifiers,
                statusResolver,
                mappers);
    }

    /** 记录透明解包次数，避免状态与协议头分别重复遍历。 */
    private static class CountingWrapper extends CompletionException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private final AtomicInteger causeReads = new AtomicInteger();

        CountingWrapper(final Throwable cause) {
            super(cause);
        }

        @Override
        public synchronized @Nullable Throwable getCause() {
            this.causeReads.incrementAndGet();
            return super.getCause();
        }
    }

    /** 外层声明本身就是协议边界，不能继续借用内部异常的头。 */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class DeclaredWrapper extends CompletionException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        DeclaredWrapper(final Throwable cause) {
            super(cause);
        }
    }

    /** 提供原生响应模式的处理器元数据。 */
    static class NativeController {
        /** 声明测试使用的原生响应出口。 */
        @NativeResponse(NativeResponseMode.ALL)
        public void endpoint() {}
    }

    /** 提供返回值约束元数据。 */
    static class ValidatedService {
        /** 声明不允许为空的返回值。 */
        @NotNull(message = "secret return constraint")
        public String result() {
            return "value";
        }

        /** 提供请求参数约束元数据。 */
        public void accept(@NotNull(message = "required input") final String value) {}
    }

    /** 提供带内部原因文本的状态注解。 */
    @ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "secret reason")
    private static class MissingResourceException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
    }
}
