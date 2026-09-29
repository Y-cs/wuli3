package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.web.error.WebErrorMapper;
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
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    /** 参数异常默认四百，内部故障及包含参数异常的未知包装仍为五百。 */
    @Test
    void classifiesOnlyTopLevelArgumentErrors() {
        final WebExceptionHandler handler = WebErrorBoundaryTest.handler(List.of());
        final MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(handler.handleException(new NumberFormatException("secret"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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

    /** 创建使用真实 core 投影和 HTTP 分类的处理器。 */
    private static WebExceptionHandler handler(final List<WebErrorMapper> mappers) {
        final InvocationContextAccessor accessor = mock(InvocationContextAccessor.class);
        return new WebExceptionHandler(
                new ApiResponseFactory(accessor, new ErrorResolver("")),
                accessor,
                new WebResponseProperties(),
                List.of(),
                new DefaultWebErrorStatusResolver(),
                mappers);
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
        @NotNull
        public String result() {
            return "value";
        }
    }
}
