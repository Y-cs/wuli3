package com.kjs.wuli3.web.internal.filter;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.context.ClientIpResolver;
import com.kjs.wuli3.web.context.RequestIdResolver;
import com.kjs.wuli3.web.context.RequestIds;
import com.kjs.wuli3.web.context.WebContextProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 在 Servlet 请求期间建立并清理 Wuli3 调用上下文。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
public final class ContextFilter extends OncePerRequestFilter {

    private final ContextManager contextManager;
    private final AuthContextResolver authContextResolver;
    private final RequestIdResolver requestIdResolver;
    private final ClientIpResolver clientIpResolver;
    private final WebContextProperties contextProperties;
    private final HandlerExceptionResolver exceptionResolver;

    /** 创建可将过滤器异常交给 Spring 统一错误响应链处理的过滤器。 */
    public ContextFilter(
            final ContextManager contextManager,
            final AuthContextResolver authContextResolver,
            final RequestIdResolver requestIdResolver,
            final ClientIpResolver clientIpResolver,
            final WebContextProperties contextProperties,
            final HandlerExceptionResolver exceptionResolver) {
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager");
        this.authContextResolver = Objects.requireNonNull(authContextResolver, "authContextResolver");
        this.requestIdResolver = Objects.requireNonNull(requestIdResolver, "requestIdResolver");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.contextProperties = Objects.requireNonNull(contextProperties, "contextProperties");
        this.exceptionResolver = Objects.requireNonNull(exceptionResolver, "exceptionResolver");
    }

    /**
     * 建立请求上下文、认证上下文和 MDC，并在回调结束后恢复上下文状态。
     *
     * <p>执行顺序：
     * <ol>
     *   <li>解析或生成 requestId，写入 {@link InvocationContext}</li>
     *   <li>解析客户端 IP（仅信任配置的代理网段转发头）</li>
     *   <li>将 requestId 写入响应头和 MDC（供日志框架使用）</li>
     *   <li>解析认证上下文（可选，默认从可信内部请求头恢复）</li>
     *   <li>执行过滤器链（业务代码可通过 Accessor 读取上下文）</li>
     *   <li><strong>finally 清理</strong>：移除 MDC；上下文作用域由回调自动恢复</li>
     * </ol>
     *
     * <p><strong>清理策略</strong>：无论过滤器链是否抛出异常，都必须移除 MDC；上下文由作用域自动恢复。
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        final String requestId = this.requestIdResolver.resolve(request);
        final InvocationContext invocationContext =
                new InvocationContext(this.clientIpResolver.resolve(request), requestId);
        response.setHeader(this.contextProperties.getRequestIdHeaderName(), requestId);
        MDC.put(RequestIds.MDC_KEY, requestId);
        try {
            final ContextState state = this.authContextResolver
                    .resolve(request)
                    .map(auth -> ContextState.of(invocationContext, auth))
                    .orElseGet(() -> ContextState.of(invocationContext));
            this.contextManager.with(state).call(() -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (final Exception exception) {
            final ModelAndView resolved = this.exceptionResolver.resolveException(request, response, this, exception);
            if (resolved != null) {
                return;
            }
            switch (exception) {
                case IOException ioException -> throw ioException;
                case ServletException servletException -> throw servletException;
                case RuntimeException runtimeException -> throw runtimeException;
                default -> {}
            }
            throw new ServletException(exception);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
