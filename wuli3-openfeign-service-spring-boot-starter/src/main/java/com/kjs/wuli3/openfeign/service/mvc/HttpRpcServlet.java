package com.kjs.wuli3.openfeign.service.mvc;

import com.kjs.wuli3.json.provider.JacksonProvider;
import com.kjs.wuli3.openfeign.service.HttpRpcAuthenticator;
import com.kjs.wuli3.openfeign.service.HttpRpcService;
import com.kjs.wuli3.openfeign.service.context.HttpRpcRequestContext;
import com.kjs.wuli3.openfeign.service.error.HttpRpcResponses;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.spring.logging.RequestIdMdc;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * 用无父容器的 MVC 配置执行内部同步调用，并负责整个调用期间的上下文安装和还原。
 *
 * 注意：只借用应用容器已创建的服务实例，不再次执行其初始化或销毁回调；公开 advice、converter 和路由不可见。
 * 请求依次经过服务认证、协议与上下文校验、作用域安装、MVC 调用及作用域还原。
 * Servlet 外部的全局 Filter 和安全链仍由应用管理，独立 MVC 不会隔离这些组件。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@NullMarked
public final class HttpRpcServlet extends DispatcherServlet {

    @Serial
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> services;
    private final HttpRpcAuthenticator authenticator;
    private final ContextManager contextManager;
    private final HttpRpcRequestContext requestContext;
    private final HttpRpcResponses responses;

    /** 使用应用已初始化的显式服务和公共运行环境创建独立入口。 */
    public HttpRpcServlet(
            final Map<String, Object> services,
            final HttpRpcAuthenticator authenticator,
            final ContextManager contextManager,
            final ContextPropagator propagator,
            final String serviceCode) {
        this.services = Map.copyOf(services);
        // 在应用容器装配时拒绝双重出口，避免仅 Servlet 初始化失败而公开 MVC 仍暴露该服务。
        for (final Object service : this.services.values()) {
            final Class<?> type = AopUtils.getTargetClass(service);
            if (!AnnotatedElementUtils.hasAnnotation(type, HttpRpcService.class)
                    || AnnotatedElementUtils.hasAnnotation(type, Controller.class)) {
                throw new IllegalStateException(
                        "Internal HTTP service must only use @HttpRpcService: " + type.getName());
            }
            if (AopUtils.isJdkDynamicProxy(service)) {
                throw new IllegalStateException(
                        "Internal HTTP service requires a class-based proxy: " + type.getName());
            }
        }
        this.authenticator = authenticator;
        this.contextManager = contextManager;
        this.requestContext = new HttpRpcRequestContext(propagator);
        this.responses = new HttpRpcResponses(serviceCode);
        this.setDetectAllHandlerMappings(false);
        this.setDetectAllHandlerAdapters(false);
        this.setDetectAllHandlerExceptionResolvers(false);
        this.setPublishContext(false);
    }

    /**
     * 创建并刷新无父容器的 MVC 上下文，只注册内部调用所需的基础组件。
     * <p>
     * 注意：仅关闭自动发现不足以隔离父容器中的公开 Web 组件，因此此处不使用传入的应用根容器。
     * 服务以已创建的单例实例借入，保留原有依赖和代理，不让内部容器重新创建或管理其销毁。
     */
    @Override
    protected WebApplicationContext createWebApplicationContext(final @Nullable ApplicationContext ignoredParent) {
        final GenericWebApplicationContext context = new GenericWebApplicationContext();
        context.setServletContext(this.getServletContext());
        context.setServletConfig(this.getServletConfig());
        for (final Map.Entry<String, Object> entry : this.services.entrySet()) {
            context.getBeanFactory().registerSingleton("rpcService:" + entry.getKey(), entry.getValue());
        }
        context.registerBean(DispatcherServlet.HANDLER_MAPPING_BEAN_NAME, HttpRpcHandlerMapping.class);
        context.registerBean(
                "rpcValidator", org.springframework.validation.beanvalidation.LocalValidatorFactoryBean.class);
        context.registerBean(DispatcherServlet.HANDLER_ADAPTER_BEAN_NAME, RequestMappingHandlerAdapter.class, () -> {
            final RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
            adapter.setMessageConverters(
                    List.of(new MappingJackson2HttpMessageConverter(JacksonProvider.newJsonMapper())));
            final org.springframework.web.bind.support.ConfigurableWebBindingInitializer initializer =
                    new org.springframework.web.bind.support.ConfigurableWebBindingInitializer();
            initializer.setConversionService(
                    new org.springframework.format.support.DefaultFormattingConversionService());
            initializer.setValidator(context.getBean("rpcValidator", org.springframework.validation.Validator.class));
            adapter.setWebBindingInitializer(initializer);
            return adapter;
        });
        context.getBeanFactory()
                .registerSingleton(DispatcherServlet.HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, this.responses);
        context.refresh();
        return context;
    }

    /**
     * 先认证再解码上下文，将参数绑定、业务执行和响应序列化纳入同一同步作用域。
     * <p>
     * 注意：MDC 和运行上下文均恢复为调用前状态，不能简单清空，以免破坏当前线程已有的外层作用域。
     * MVC 内部异常由独立解析器处理；认证、协议解析或调度时逃逸的异常由外层捕获并尝试写入内部错误协议。
     */
    @Override
    @SuppressWarnings("try")
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        // MVC 的 sendError 也必须使用内部协议，防止容器将错误分派到公开 /error。
        final HttpServletResponse guarded = new HttpServletResponseWrapper(response) {
            @Override
            public void sendError(final int status, final @Nullable String message) throws IOException {
                HttpRpcServlet.this.responses.status(this, status);
            }

            @Override
            public void sendError(final int status) throws IOException {
                HttpRpcServlet.this.responses.status(this, status);
            }
        };
        try {
            if (!this.authenticator.authenticate(request)) {
                this.responses.status(guarded, 401);
                return;
            }
            final Optional<ContextSnapshot> snapshot = this.requestContext.read(request);
            if (snapshot.isEmpty()) {
                this.responses.status(guarded, 400);
                return;
            }
            final ContextSnapshot restored = snapshot.orElseThrow();
            // 空快照同样需要安装，保证工作线程残留的外层身份不会被本次请求继承。
            try (final RequestIdMdc ignored = RequestIdMdc.open(restored)) {
                this.contextManager.from(restored).call(() -> {
                    super.service(request, guarded);
                    return null;
                });
            }
        } catch (final Exception exception) {
            // 走到这里时已退出调用作用域；已提交响应或写出失败时，保留原异常交给上层处理。
            if (this.responses.resolveException(request, guarded, null, exception) == null) {
                if (exception instanceof IOException io) {
                    throw io;
                }
                throw new ServletException(exception);
            }
        }
    }
}
