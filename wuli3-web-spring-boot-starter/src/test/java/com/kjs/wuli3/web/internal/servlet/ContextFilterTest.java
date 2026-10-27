package com.kjs.wuli3.web.internal.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.context.WebContextProperties;
import com.kjs.wuli3.web.internal.filter.ContextFilter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

class ContextFilterTest {

    @Test
    void doesNotCreateAuthenticationContextWhenResolverReturnsEmpty() throws Exception {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        final ContextFilter filter = ContextFilterTest.filter(threadLocalContextStore, request -> Optional.empty());

        filter.doFilter(
                new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), (request, response) -> {
                    assertThat(threadLocalContextStore.get(ContextKey.of(InvocationContext.class)))
                            .map(InvocationContext::requestId)
                            .contains("rid-1");
                    assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class)))
                            .isEmpty();
                });

        assertThat(threadLocalContextStore.get(ContextKey.of(InvocationContext.class)))
                .isEmpty();
        assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class)))
                .isEmpty();
    }

    @Test
    void storesAuthenticationContextWhenApplicationProvidesResolver() throws Exception {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        final AuthContextResolver authContextResolver =
                request -> Optional.of(new AuthContext(PrincipalType.CUSTOMER, "7"));
        final ContextFilter filter = ContextFilterTest.filter(threadLocalContextStore, authContextResolver);

        filter.doFilter(
                new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), (request, response) -> {
                    assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class)))
                            .map(AuthContext::principalId)
                            .contains("7");
                });

        assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class)))
                .isEmpty();
    }

    /** 过滤器内抛出的业务异常必须交给全局异常处理，不能退化为容器内部错误响应。 */
    @Test
    void routesFilterExceptionToGlobalExceptionHandler() throws Exception {
        try (final GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("contextFilterAdvice", ContextFilterTest.FilterAdvice.class);
            context.refresh();
            final ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
            resolver.setApplicationContext(context);
            resolver.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
            resolver.afterPropertiesSet();

            final ContextFilter filter = ContextFilterTest.filter(
                    new ThreadLocalContextBackend(),
                    request -> {
                        throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT);
                    },
                    resolver);

            final MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/orders"), response, (request, responseArg) -> {});

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("handled:非法参数异常");
        }
    }

    @RestControllerAdvice
    static class FilterAdvice {

        @ExceptionHandler(ErrorCodeException.class)
        ResponseEntity<String> handle(final ErrorCodeException exception) {
            return ResponseEntity.status(400)
                    .body("handled:" + exception.getErrorCode().getMessage());
        }
    }

    private static ContextFilter filter(
            final ThreadLocalContextBackend threadLocalContextStore, final AuthContextResolver authContextResolver) {
        return ContextFilterTest.filter(
                threadLocalContextStore, authContextResolver, (request, response, handler, exception) -> null);
    }

    private static ContextFilter filter(
            final ThreadLocalContextBackend threadLocalContextStore,
            final AuthContextResolver authContextResolver,
            final HandlerExceptionResolver exceptionResolver) {
        return new ContextFilter(
                new ContextManager(threadLocalContextStore, threadLocalContextStore),
                authContextResolver,
                request -> "rid-1",
                request -> "127.0.0.1",
                new WebContextProperties(),
                exceptionResolver);
    }
}
