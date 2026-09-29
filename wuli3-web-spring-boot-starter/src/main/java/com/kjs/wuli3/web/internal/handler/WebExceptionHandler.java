package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.web.error.ErrorAlertContext;
import com.kjs.wuli3.web.error.ErrorAlertNotifier;
import com.kjs.wuli3.web.error.WebErrorMapper;
import com.kjs.wuli3.web.error.WebErrorStatusResolver;
import com.kjs.wuli3.web.error.WebErrors;
import com.kjs.wuli3.web.internal.advice.ApiResponseFactory;
import com.kjs.wuli3.web.internal.advice.NativeResponseSupport;
import com.kjs.wuli3.web.internal.advice.ValidationErrorDetailsFactory;
import com.kjs.wuli3.web.internal.error.ErrorAlertNotifiers;
import com.kjs.wuli3.web.internal.error.WebErrorResponseMapper;
import com.kjs.wuli3.web.response.ApiResponse;
import com.kjs.wuli3.web.response.WebResponseProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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
        final HttpStatus status;
        final ErrorCodeException semanticError;
        if (mapped != null) {
            semanticError = mapped;
            status = this.webErrorStatusResolver.resolve(mapped, mapped.getErrorCode());
        } else {
            status = this.webErrorStatusResolver.resolve(ex, WebErrors.INTERNAL_ERROR);
            final ErrorCode code = ex instanceof IllegalArgumentException && status == HttpStatus.BAD_REQUEST
                    ? CommonErrors.ILLEGAL_ARGUMENT
                    : WebErrorResponseMapper.responseCode(status);
            // 不把库异常消息或框架 ProblemDetail 当成可直接公开的业务消息。
            semanticError = new ErrorCodeException(code, code.getMessage(), ex);
        }
        final ErrorCodeCarrier visible = this.responseFactory.resolve(semanticError);
        final Object detail = semanticError.getVisibility() == ErrorVisibility.PUBLIC && status.is4xxClientError()
                ? this.validationErrorDetailsFactory.detail(ex)
                : null;
        final ApiResponse<?> response = ApiResponse.failure(visible, this.requestId(), detail);
        this.alert(ex, semanticError, request, status, visible);
        final HttpHeaders headers = new HttpHeaders();
        if (ex instanceof ErrorResponse frameworkError) {
            // 仅保留协议协商需要的头，诊断头不能绕过正文脱敏。
            for (final String name : List.of(
                    HttpHeaders.ALLOW, HttpHeaders.ACCEPT, HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE)) {
                final List<String> values = frameworkError.getHeaders().get(name);
                if (values != null) {
                    headers.put(name, values);
                }
            }
        }
        if (NativeResponseSupport.isAll(request)) {
            return ResponseEntity.status(status)
                    .headers(headers)
                    .body(WebErrorResponseMapper.nativeError(status, response).getBody());
        }
        return ResponseEntity.status(status).headers(headers).body(response);
    }

    /** 显式边界映射优先；未匹配时保留错误自身的责任和可见性。 */
    private @Nullable ErrorCodeException map(final Exception error) {
        for (final WebErrorMapper mapper : this.errorMappers) {
            final ErrorCodeException mapped = mapper.map(error);
            if (mapped != null) {
                return mapped;
            }
        }
        return error instanceof ErrorCodeException coded ? coded : null;
    }

    /** 按最终责任和严重程度告警，同时保留原始异常用于内部诊断。 */
    private void alert(
            final Throwable original,
            final ErrorCodeException semanticError,
            final HttpServletRequest request,
            final HttpStatus status,
            final ErrorCode responseCode) {
        final ErrorSeverity severity = semanticError.getSeverity();
        if (!status.is5xxServerError() && severity != ErrorSeverity.CRITICAL && severity != ErrorSeverity.FATAL) {
            return;
        }
        this.errorAlertNotifiers.dispatch(new ErrorAlertContext(
                original,
                status,
                responseCode,
                this.requestId(),
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr()));
    }

    /** 获取当前请求的关联标识。 */
    private @Nullable String requestId() {
        return this.invocationContextAccessor.requestId().orElse(null);
    }
}
