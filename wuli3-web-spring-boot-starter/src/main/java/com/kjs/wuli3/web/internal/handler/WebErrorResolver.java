package com.kjs.wuli3.web.internal.handler;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorCode;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.web.error.WebErrors;
import com.kjs.wuli3.web.internal.error.WebErrorResponseMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 为 MVC 出口一次识别错误语义、HTTP 状态和协议头。
 *
 * <p>当前异常的明确声明形成边界；只有异步和反射透明包装允许继续解包。普通异常及裸参数异常均不推断内部请求语义。
 * 业务错误完整保留其元数据，协议错误仅采用状态与白名单头，不公开库异常消息。
 *
 * @author 国杨 create on 2026/11/3 11:40
 */
final class WebErrorResolver {

    private static final List<String> PROTOCOL_HEADERS =
            List.of(HttpHeaders.ALLOW, HttpHeaders.ACCEPT, HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE);

    private WebErrorResolver() {}

    /** 为默认 HTTP 出口生成完整决策，未知错误不借用业务错误码表达中间状态。 */
    static WebErrorResolution resolve(final Throwable error) {
        final WebErrorResolution resolution = WebErrorResolver.find(error);
        return resolution == null
                ? WebErrorResolution.protocol(error, HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, false)
                : resolution;
    }

    /** 当前层声明优先于透明解包；null 明确表示未识别，不携带占位错误码。 */
    private static @Nullable WebErrorResolution find(final Throwable error) {
        Throwable current = error;
        Set<Throwable> visited = null;
        while (true) {
            if (current instanceof ErrorCodeException business) {
                return new WebErrorResolution(
                        business,
                        business.getErrorCode(),
                        WebErrorResolver.businessStatus(business),
                        HttpHeaders.EMPTY,
                        false);
            }
            if (current instanceof ErrorResponse protocol) {
                final HttpStatus status = WebErrorResponseMapper.normalize(
                        protocol.getStatusCode().value());
                return WebErrorResolution.protocol(
                        current, status, WebErrorResolver.protocolHeaders(protocol, status), status.is4xxClientError());
            }
            final ResponseStatus annotation =
                    AnnotatedElementUtils.findMergedAnnotation(current.getClass(), ResponseStatus.class);
            if (annotation != null) {
                return WebErrorResolution.protocol(
                        current,
                        WebErrorResponseMapper.normalize(annotation.code().value()),
                        HttpHeaders.EMPTY,
                        false);
            }
            final HttpStatus securityStatus = WebErrorResolver.securityStatus(current);
            if (securityStatus != null) {
                return WebErrorResolution.protocol(current, securityStatus, HttpHeaders.EMPTY, false);
            }
            if (WebErrorResolver.requestError(current)) {
                return WebErrorResolution.protocol(current, HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, true);
            }
            if (!(current instanceof CompletionException
                    || current instanceof ExecutionException
                    || current instanceof InvocationTargetException)) {
                return null;
            }
            // 常见直接异常无需去重集合；仅透明解包时按实例身份检测循环。
            if (visited == null) {
                visited = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            if (!visited.add(current)) {
                return null;
            }
            final Throwable cause = current.getCause();
            if (cause == null) {
                return null;
            }
            current = cause;
        }
    }

    /** 本地协议状态必须与最终责任系列一致；远程错误不根据名称猜测状态。 */
    private static HttpStatus businessStatus(final ErrorCodeException business) {
        final HttpStatus status = WebErrorResolver.webStatus(business.getErrorCode());
        if (business.getOrigin() == ErrorOrigin.SERVER) {
            return status != null && status.is5xxServerError() ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return status != null && status.is4xxClientError() ? status : HttpStatus.BAD_REQUEST;
    }

    /** 只有本地 Web 枚举声明了协议状态，其他错误按责任处理。 */
    private static @Nullable HttpStatus webStatus(final ErrorCode code) {
        return code instanceof WebErrors web ? web.getHttpStatus() : null;
    }

    /** 只识别明确的请求输入错误；返回值约束和空校验集合不能证明调用方有错。 */
    private static boolean requestError(final Throwable error) {
        if (error instanceof ConstraintViolationException violation) {
            return !violation.getConstraintViolations().isEmpty()
                    && violation.getConstraintViolations().stream().noneMatch(item -> {
                        for (final jakarta.validation.Path.Node node : item.getPropertyPath()) {
                            if (node.getKind() == ElementKind.RETURN_VALUE) {
                                return true;
                            }
                        }
                        return false;
                    });
        }
        return error instanceof MethodArgumentTypeMismatchException || error instanceof HttpMessageNotReadableException;
    }

    /** 状态与白名单头必须来自同一个已选协议异常，归一化改变状态时不复制旧头。 */
    private static HttpHeaders protocolHeaders(final ErrorResponse protocol, final HttpStatus status) {
        if (!status.isSameCodeAs(protocol.getStatusCode())) {
            return HttpHeaders.EMPTY;
        }
        final HttpHeaders headers = new HttpHeaders();
        final HttpHeaders source = protocol.getHeaders();
        for (final String name : WebErrorResolver.PROTOCOL_HEADERS) {
            final List<String> values = source.get(name);
            if (values != null) {
                headers.put(name, List.copyOf(values));
            }
        }
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }

    /** Web 不强制依赖 Spring Security，按完整类名识别当前异常及其类型父类。 */
    private static @Nullable HttpStatus securityStatus(final Throwable error) {
        Class<?> type = error.getClass();
        while (type != null) {
            switch (type.getName()) {
                case "org.springframework.security.core.AuthenticationException" -> {
                    return HttpStatus.UNAUTHORIZED;
                }
                case "org.springframework.security.access.AccessDeniedException" -> {
                    return HttpStatus.FORBIDDEN;
                }
                default -> {}
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
