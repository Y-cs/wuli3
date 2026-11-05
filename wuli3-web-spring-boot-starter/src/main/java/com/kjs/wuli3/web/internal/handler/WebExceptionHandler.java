package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.web.error.ErrorAlertContext;
import com.kjs.wuli3.web.error.ErrorAlertNotifier;
import com.kjs.wuli3.web.error.WebErrorMapper;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import com.kjs.wuli3.web.internal.advice.ApiResponseFactory;
import com.kjs.wuli3.web.internal.advice.NativeResponseSupport;
import com.kjs.wuli3.web.internal.advice.ValidationErrorDetailsFactory;
import com.kjs.wuli3.web.internal.error.ErrorAlertNotifiers;
import com.kjs.wuli3.web.internal.error.WebErrorResponseMapper;
import com.kjs.wuli3.web.response.ApiResponse;
import com.kjs.wuli3.web.response.WebResponseProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 识别 Web 异常，并在最终 HTTP 出口统一执行可见性过滤。
 *
 * 注意：内部 RPC 携带完整错误信息；本类才生成公开响应，不直接返回异常中的 ProblemDetail。
 * 开发者映射优先，其次使用错误自身元数据，最后按框架异常分类。未知异常默认返回内部错误。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@RestControllerAdvice
@Slf4j
public class WebExceptionHandler {

    private final ApiResponseFactory responseFactory;
    private final InvocationContextAccessor invocationContextAccessor;
    private final WebErrorStatusResolver webErrorStatusResolver;
    private final ValidationErrorDetailsFactory validationErrorDetailsFactory;
    private final ErrorAlertNotifiers errorAlertNotifiers;
    private final List<WebErrorMapper> errorMappers;

    /** 使用已排序的开发者映射创建处理器。 */
    public WebExceptionHandler(
            final ApiResponseFactory responseFactory,
            final InvocationContextAccessor invocationContextAccessor,
            final WebResponseProperties responseProperties,
            final List<ErrorAlertNotifier> errorAlertNotifiers,
            final WebErrorStatusResolver webErrorStatusResolver,
            final List<WebErrorMapper> errorMappers) {
        this.responseFactory = responseFactory;
        this.invocationContextAccessor = invocationContextAccessor;
        this.webErrorStatusResolver = webErrorStatusResolver;
        this.validationErrorDetailsFactory = new ValidationErrorDetailsFactory(responseProperties);
        this.errorAlertNotifiers = new ErrorAlertNotifiers(errorAlertNotifiers);
        this.errorMappers = List.copyOf(errorMappers);
    }

    /** 将所有 MVC 异常通过同一投影路径输出，避免详情绕过可见性策略。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(final Exception ex, final HttpServletRequest request) {
        final ErrorCodeException mapped = this.map(ex);
        final WebErrorResolution resolution = this.resolve(mapped == null ? ex : mapped);
        final ErrorCodeCarrier visible = resolution.source() instanceof ErrorCodeException business
                ? this.responseFactory.resolve(business)
                : this.responseFactory.resolve(
                        resolution.code(), resolution.code().getMessage());
        final Object detail = resolution.validationDetailsAllowed()
                        && visible.visibility() == ErrorVisibility.PUBLIC
                        && resolution.status().is4xxClientError()
                ? this.validationErrorDetailsFactory.detail(resolution.source())
                : null;
        final String requestId = this.requestId();
        final ApiResponse<?> response = ApiResponse.failure(visible, requestId, detail);
        this.alert(ex, request, resolution.status(), visible, requestId);
        // 只保留框架协议要求的只读白名单头，不把内部错误传播协议暴露到 HTTP 边界。
        if (NativeResponseSupport.isAll(request)) {
            return ResponseEntity.status(resolution.status())
                    .headers(resolution.headers())
                    .body(WebErrorResponseMapper.nativeError(resolution.status(), response)
                            .getBody());
        }
        return ResponseEntity.status(resolution.status()).headers(resolution.headers()).body(response);
    }

    /**
     * 默认实现复用完整识别结果；自定义状态 SPI 在识别后调用一次，只显式覆盖 HTTP 状态。
     *
     * <p>保留原 SPI 的异常输入和业务码语义。框架错误传入已经识别的协议码，且非错误状态不能作为最终错误响应。
     */
    private WebErrorResolution resolve(final Throwable error) {
        final WebErrorResolution resolution = WebErrorResolver.resolve(error);
        if (this.webErrorStatusResolver instanceof DefaultWebErrorStatusResolver) {
            return resolution;
        }
        final HttpStatus status = this.webErrorStatusResolver.resolve(error, resolution.code());
        return resolution.withStatus(WebErrorResponseMapper.normalize(status.value()));
    }

    /** 按既有排序执行显式映射，命中后不再从原始异常继承语义或协议头。 */
    private @Nullable ErrorCodeException map(final Exception error) {
        for (final WebErrorMapper mapper : this.errorMappers) {
            final ErrorCodeException mapped = mapper.map(error);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /** 按最终状态和严重度记录结构化诊断并通知告警，保留原始异常堆栈。 */
    private void alert(
            final Throwable original,
            final HttpServletRequest request,
            final HttpStatus status,
            final ErrorCodeCarrier responseCode,
            final @Nullable String requestId) {
        final ErrorSeverity severity = responseCode.severity();
        if (!status.is5xxServerError() && severity != ErrorSeverity.CRITICAL && severity != ErrorSeverity.FATAL) {
            return;
        }
        // 原始码与公开响应码分别记录；关联字段避免与既有 MDC requestId 重名，兼容 ECS 嵌套编码。
        WebExceptionHandler.log
                .atError()
                .addKeyValue("eventName", "web.request.failed")
                .addKeyValue("httpStatus", status.value())
                .addKeyValue("errorCode", responseCode.originalCode())
                .addKeyValue("responseCode", responseCode.code())
                .addKeyValue("errorOrigin", responseCode.origin().name())
                .addKeyValue("errorSeverity", severity.name())
                .addKeyValue("errorVisibility", responseCode.visibility().name())
                .addKeyValue("sourceService", responseCode.sourceService())
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("httpPath", request.getRequestURI())
                .addKeyValue("httpRequestId", requestId)
                .addKeyValue("exceptionType", original.getClass().getName())
                .setCause(original)
                .log("Web request failed");
        this.errorAlertNotifiers.dispatch(new ErrorAlertContext(
                original,
                status,
                responseCode,
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr()));
    }

    /** 获取当前请求的关联标识。 */
    private @Nullable String requestId() {
        return this.invocationContextAccessor.requestId().orElse(null);
    }
}
