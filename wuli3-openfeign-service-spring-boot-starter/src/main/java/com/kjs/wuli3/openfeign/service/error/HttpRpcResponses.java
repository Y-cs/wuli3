package com.kjs.wuli3.openfeign.service.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.json.provider.JacksonProvider;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 把内部调用失败写成非 2xx 版本化协议，不经过公开 Web 的错误投影。
 *
 * 注意：业务错误交给共享 ErrorResolver 生成完整传播载荷，错误可见性留到最终公开出口投影。
 * 本类同时处理 MVC 异常和 Servlet 主动拒绝的状态，避免两条失败路径产生不同的响应格式。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@NullMarked
public final class HttpRpcResponses implements HandlerExceptionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRpcResponses.class);
    private final ErrorResolver resolver;
    private final ObjectMapper mapper = JacksonProvider.newJsonMapper();
    private final ErrorCodePropagator propagator = new ErrorCodePropagator();

    /** 使用应用服务标识创建内部错误出口；该类型供 Servlet 装配，不是业务扩展点。 */
    public HttpRpcResponses(final String serviceCode) {
        this.resolver = new ErrorResolver(serviceCode);
    }

    /**
     * 保留已知业务错误；绑定、校验和协议失败按状态分类，未知异常记录日志后输出安全错误。
     *
     * @return 空 ModelAndView 表示已处理；null 表示响应已提交或写出失败，调用方需继续处理原异常
     */
    @Override
    public @Nullable ModelAndView resolveException(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final @Nullable Object handler,
            final Exception exception) {
        if (response.isCommitted()) {
            return null;
        }
        try {
            if (exception instanceof ErrorCodeException business) {
                final ErrorCodeCarrier error = this.resolver.resolvePropagation(business);
                this.write(response, error.origin() == ErrorOrigin.CALLER ? 400 : 500, error);
            } else if (exception instanceof org.springframework.http.converter.HttpMessageNotReadableException
                    || exception instanceof org.springframework.web.bind.ServletRequestBindingException
                    || exception instanceof org.springframework.beans.TypeMismatchException
                    || exception instanceof org.springframework.validation.BindException) {
                this.status(response, 400);
            } else if (exception instanceof ErrorResponse protocol) {
                protocol.getHeaders().forEach((name, values) -> {
                    if ("Allow".equalsIgnoreCase(name) || "Accept".equalsIgnoreCase(name)) {
                        values.forEach(value -> response.addHeader(name, value));
                    }
                });
                this.status(response, protocol.getStatusCode().value());
            } else {
                HttpRpcResponses.LOG.error("Internal HTTP invocation failed", exception);
                this.status(response, 500);
            }
            return new ModelAndView();
        } catch (final IOException writeFailure) {
            HttpRpcResponses.LOG.warn("Unable to write internal HTTP failure", writeFailure);
            return null;
        }
    }

    /** 输出没有业务错误详情的协议失败，避免容器转发至公开 /error。 */
    public void status(final HttpServletResponse response, final int status) throws IOException {
        final HttpRpcErrors error =
                switch (status) {
                    case 400 -> HttpRpcErrors.INVALID_REQUEST;
                    case 401, 403 -> HttpRpcErrors.UNAUTHENTICATED;
                    case 404 -> HttpRpcErrors.NOT_FOUND;
                    case 405 -> HttpRpcErrors.METHOD_NOT_ALLOWED;
                    case 406, 415 -> HttpRpcErrors.UNSUPPORTED_MEDIA_TYPE;
                    default -> HttpRpcErrors.INTERNAL_ERROR;
                };
        this.write(
                response,
                status >= 400 && status <= 599 ? status : 500,
                this.resolver.resolvePropagation(new ErrorCodeException(error, error.getMessage())));
    }

    /**
     * 完成编码及大小检查后替换未提交的响应正文，通过版本头和独立媒体类型标记内部错误。
     *
     * 注意：业务代码可能已写入缓冲，必须先清除旧正文；原错误及安全替换文档都验证大小后才修改响应。
     * 输出流写入本身仍可能失败，完整编码只保证编码失败或超限时不会先写出部分协议文档。
     */
    private void write(final HttpServletResponse response, final int status, final ErrorCodeCarrier error)
            throws IOException {
        final byte[] encoded = this.encodeError(error);
        final byte[] body;
        final int finalStatus;
        if (encoded.length > HttpRpcProtocol.MAX_ERROR_BYTES) {
            // 仅正文超限触发安全替换；编码和输出故障保留真实异常供上层记录。
            HttpRpcResponses.LOG.warn("Internal HTTP error document exceeds protocol limit");
            body = this.encodeError(this.resolver.resolvePropagation(
                    new ErrorCodeException(HttpRpcErrors.INTERNAL_ERROR, HttpRpcErrors.INTERNAL_ERROR.getMessage())));
            if (body.length > HttpRpcProtocol.MAX_ERROR_BYTES) {
                throw new IOException("Internal HTTP fallback error exceeds protocol limit");
            }
            finalStatus = 500;
        } else {
            body = encoded;
            finalStatus = status;
        }
        response.resetBuffer();
        response.setStatus(finalStatus);
        response.setHeader(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
        response.setContentType(HttpRpcProtocol.ERROR_CONTENT_TYPE);
        response.getOutputStream().write(body);
    }

    /** 仅生成完整文档字节，不接触 Servlet 响应；长度检查和实际写出由调用方完成。 */
    private byte[] encodeError(final ErrorCodeCarrier error) throws JsonProcessingException {
        final Map<String, String> fields = new LinkedHashMap<>();
        this.propagator.inject(error, fields::put);
        return this.mapper.writeValueAsBytes(Map.of("version", HttpRpcProtocol.VERSION, "error", fields));
    }
}
