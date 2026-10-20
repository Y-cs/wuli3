package com.kjs.wuli3.web.internal.interceptor;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.web.auth.LoginRequired;
import com.kjs.wuli3.web.error.WebErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拒绝未认证请求访问标记了 {@link LoginRequired} 的 MVC 接口。
 *
 * 注意：认证结果由 {@link com.kjs.wuli3.web.auth.AuthContextResolver} 提供；本类只判断认证上下文是否存在，
 * 不执行认证。拦截范围仅限 Spring MVC 的 {@link HandlerMethod}。
 *
 * @author lipt create on 2026/10/20 10:00
 */
public final class LoginRequiredInterceptor implements HandlerInterceptor {

    private final AuthContextAccessor authContextAccessor;

    /** 使用当前请求的认证上下文访问器创建拦截器。 */
    public LoginRequiredInterceptor(final AuthContextAccessor authContextAccessor) {
        this.authContextAccessor = Objects.requireNonNull(authContextAccessor, "authContextAccessor");
    }

    /**
     * 标记了 {@link LoginRequired} 的处理器缺少认证上下文时拒绝请求。
     *
     * @throws ErrorCodeException 未认证时抛出 {@link WebErrors#UNAUTHORIZED}
     */
    @Override
    public boolean preHandle(
            final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        if (handler instanceof HandlerMethod handlerMethod
                && LoginRequiredInterceptor.isLoginRequired(handlerMethod)
                && this.authContextAccessor.current().isEmpty()) {
            throw new ErrorCodeException(WebErrors.UNAUTHORIZED);
        }
        return true;
    }

    private static boolean isLoginRequired(final HandlerMethod handlerMethod) {
        // getBeanType 已还原 CGLIB 代理的用户类，类型级注解不会因代理丢失。
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), LoginRequired.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), LoginRequired.class);
    }
}
