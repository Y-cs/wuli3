package com.kjs.wuli3.openfeign.service.autoconfigure;

import com.kjs.wuli3.openfeign.service.HttpRpcAuthenticator;
import com.kjs.wuli3.openfeign.service.HttpRpcProperties;
import com.kjs.wuli3.openfeign.service.HttpRpcService;
import com.kjs.wuli3.openfeign.service.mvc.HttpRpcServlet;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.ContextAutoConfiguration;
import com.kjs.wuli3.spring.context.ServletContextOwnership;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 显式启用具有独立 MVC 出口的内部 HTTP RPC Servlet。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@AutoConfiguration(after = ContextAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "wuli3.http-rpc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({HttpRpcProperties.class, ApplicationServiceProperties.class})
@Import(ContextAutoConfiguration.class)
public class HttpRpcAutoConfiguration {

    /**
     * 收集应用已创建的内部服务实例，注册独占路径的同步 Servlet。
     *
     * 注意：通过容器初始化参数声明上下文由 Servlet 管理，公开 Web 的 ContextFilter 据此跳过重复安装。
     * 该参数来自服务端注册信息，请求头无法改变上下文所有权。
     */
    @Bean
    ServletRegistrationBean<HttpRpcServlet> httpRpcServletRegistration(
            final ApplicationContext application,
            final HttpRpcProperties properties,
            final HttpRpcAuthenticator authenticator,
            final ContextManager manager,
            final ContextPropagator propagator,
            final ApplicationServiceProperties service) {
        final String path = getPath(properties);
        final HttpRpcServlet servlet = new HttpRpcServlet(
                application.getBeansWithAnnotation(HttpRpcService.class),
                authenticator,
                manager,
                propagator,
                service.getServiceCode());
        final ServletRegistrationBean<HttpRpcServlet> registration =
                new ServletRegistrationBean<>(servlet, path + "/*");
        registration.setName("wuli3HttpRpc");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(false);
        registration.addInitParameter(ServletContextOwnership.INIT_PARAMETER, ServletContextOwnership.SERVLET);
        return registration;
    }

    private static @NonNull String getPath(HttpRpcProperties properties) {
        final String path = properties.getPath();
        if (path == null
                || !path.startsWith("/")
                || path.endsWith("/")
                || path.contains("*")
                || path.contains("?")
                || path.contains("#")
                || path.contains("..")
                || path.contains(";")
                || path.contains("%")
                || path.contains("\\")) {
            throw new IllegalStateException("wuli3.http-rpc.path must be a non-root absolute servlet prefix");
        }
        return path;
    }
}
