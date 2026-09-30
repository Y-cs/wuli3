package com.kjs.wuli3.web.internal.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.context.WebContextProperties;
import com.kjs.wuli3.web.internal.filter.ContextFilter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
                request -> Optional.of(new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
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

    private static ContextFilter filter(
            final ThreadLocalContextBackend threadLocalContextStore, final AuthContextResolver authContextResolver) {
        return new ContextFilter(
                new ContextManager(threadLocalContextStore, threadLocalContextStore),
                authContextResolver,
                request -> "rid-1",
                request -> "127.0.0.1",
                new WebContextProperties(),
                (request, response, handler, exception) -> null);
    }
}
