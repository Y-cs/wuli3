package com.kjs.wuli3.openfeign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.openfeign.service.autoconfigure.HttpRpcAutoConfiguration;
import com.kjs.wuli3.openfeign.service.mvc.HttpRpcServlet;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 验证显式启用、身份认证要求、独立容器与借用实例的生命周期。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
class HttpRpcAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(HttpRpcAutoConfiguration.class));

    /** 默认不注册入口，显式启用时缺少服务认证器必须启动失败。 */
    @Test
    void requiresExplicitEnablementAndAuthenticator() {
        this.runner.run(context -> assertThat(context).doesNotHaveBean("httpRpcServletRegistration"));
        this.runner
                .withPropertyValues("wuli3.http-rpc.enabled=true")
                .run(context ->
                        assertThat(context).hasFailed().getFailure().hasMessageContaining("HttpRpcAuthenticator"));
    }

    /** 无父容器的内部 Servlet 不继承根 MVC 组件，销毁时不销毁借用的业务实例。 */
    @Test
    void isolatesContextAndPreservesRootBeanLifecycle() {
        final AtomicInteger destroyed = new AtomicInteger();
        this.runner
                .withPropertyValues("wuli3.http-rpc.enabled=true")
                .withBean(HttpRpcAuthenticator.class, () -> request -> true)
                .withBean(BorrowedService.class, () -> new BorrowedService(destroyed))
                .withBean(PublicAdvice.class, PublicAdvice::new)
                .run(context -> {
                    final HttpRpcServlet servlet = (HttpRpcServlet)
                            context.getBean(ServletRegistrationBean.class).getServlet();
                    servlet.init(new MockServletConfig(context.getServletContext()));
                    assertThat(Objects.requireNonNull(servlet.getWebApplicationContext())
                                    .getParent())
                            .isNull();
                    assertThat(servlet.getWebApplicationContext().getBeansOfType(PublicAdvice.class))
                            .isEmpty();
                    final MockHttpServletRequest request =
                            new MockHttpServletRequest(context.getServletContext(), "GET", "/value");
                    request.setServletPath("");
                    request.setPathInfo("/value");
                    request.addHeader(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
                    final MockHttpServletResponse response = new MockHttpServletResponse();
                    servlet.service(request, response);
                    assertThat(response.getContentAsString()).isEqualTo("\"normal\"");
                    servlet.destroy();
                    assertThat(destroyed).hasValue(0);
                });
        assertThat(destroyed).hasValue(1);
    }

    /** 启动时拒绝同时暴露到公开 MVC 的类型，以及无法被同步作用域覆盖的接口。 */
    @Test
    void rejectsPublicAndAsynchronousExports() {
        this.runner
                .withPropertyValues("wuli3.http-rpc.enabled=true")
                .withBean(HttpRpcAuthenticator.class, () -> request -> true)
                .withBean(UnsafePublicService.class, UnsafePublicService::new)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Internal HTTP service must only use @HttpRpcService"));
        this.runner
                .withPropertyValues("wuli3.http-rpc.enabled=true")
                .withBean(HttpRpcAuthenticator.class, () -> request -> true)
                .withBean(AsyncService.class, AsyncService::new)
                .run(context -> {
                    final HttpRpcServlet servlet = (HttpRpcServlet)
                            context.getBean(ServletRegistrationBean.class).getServlet();
                    assertThatThrownBy(() -> servlet.init(new MockServletConfig(context.getServletContext())))
                            .hasStackTraceContaining("only supports synchronous JSON");
                });
    }

    /** JDK 代理无法保留导出类的映射元数据时明确失败，避免静默丢失接口。 */
    @Test
    void rejectsJdkProxyExport() {
        this.runner
                .withPropertyValues("wuli3.http-rpc.enabled=true")
                .withBean(HttpRpcAuthenticator.class, () -> request -> true)
                .run(context -> {
                    final ProxyFactory factory = new ProxyFactory(new ProxiedService());
                    final Object proxy = factory.getProxy();
                    assertThatThrownBy(() -> new HttpRpcServlet(
                                    Map.of("proxied", proxy),
                                    request -> true,
                                    context.getBean(ContextManager.class),
                                    context.getBean(ContextPropagator.class),
                                    "test"))
                            .hasMessageContaining("requires a class-based proxy");
                });
    }

    interface ExportContract {
        /** 提供代理接口，验证导出限制。 */
        String value();
    }

    @HttpRpcService
    static class ProxiedService implements ExportContract {
        /** 返回同步业务结果。 */
        @Override
        public String value() {
            return "normal";
        }
    }

    @HttpRpcService
    static class BorrowedService {
        private final AtomicInteger destroyed;

        BorrowedService(final AtomicInteger destroyed) {
            this.destroyed = destroyed;
        }
        /** 返回原始 JSON 字符串，供 advice 隔离断言使用。 */
        @GetMapping("/value")
        String value() {
            return "normal";
        }
        /** 记录应用容器唯一拥有的销毁回调。 */
        @PreDestroy
        void destroy() {
            this.destroyed.incrementAndGet();
        }
    }

    @RestControllerAdvice
    static class PublicAdvice {}

    @HttpRpcService
    @RestController
    static class UnsafePublicService {}

    @HttpRpcService
    static class AsyncService {
        /** 声明第一版内部协议不支持的异步执行。 */
        @GetMapping("/async")
        Callable<String> async() {
            return () -> "unsupported";
        }
    }
}
