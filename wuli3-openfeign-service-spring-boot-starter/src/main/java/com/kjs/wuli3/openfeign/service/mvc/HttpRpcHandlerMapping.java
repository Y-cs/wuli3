package com.kjs.wuli3.openfeign.service.mvc;

import com.kjs.wuli3.openfeign.service.HttpRpcService;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 只识别显式内部服务，并拒绝当前同步作用域无法覆盖的返回类型。
 *
 * 注意：请求路径相对于内部 Servlet 前缀，方法仍复用 Spring 请求映射注解；
 * 不要求服务标记 Controller，避免其被公开 MVC 同时发现。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
final class HttpRpcHandlerMapping extends RequestMappingHandlerMapping {

    /** 只将显式导出的内部服务纳入路由发现。 */
    @Override
    protected boolean isHandler(final Class<?> beanType) {
        return AnnotatedElementUtils.hasAnnotation(beanType, HttpRpcService.class);
    }

    /** 启动时拒绝异步和流式接口，避免业务执行脱离上下文作用域。 */
    @Override
    protected void registerHandlerMethod(
            final Object handler,
            final Method method,
            final org.springframework.web.servlet.mvc.method.RequestMappingInfo mapping) {
        final Class<?> type = method.getReturnType();
        if (Callable.class.isAssignableFrom(type)
                || CompletionStage.class.isAssignableFrom(type)
                || DeferredResult.class.isAssignableFrom(type)
                || ResponseBodyEmitter.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type)
                || org.springframework.core.ReactiveAdapterRegistry.getSharedInstance()
                                .getAdapter(type)
                        != null) {
            throw new IllegalStateException("Internal HTTP RPC only supports synchronous JSON: " + method);
        }
        super.registerHandlerMethod(handler, method, mapping);
    }
}
