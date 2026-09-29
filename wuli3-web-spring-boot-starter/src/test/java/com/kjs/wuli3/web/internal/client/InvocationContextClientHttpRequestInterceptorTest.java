package com.kjs.wuli3.web.internal.client;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.web.internal.interceptor.ContextPropagationInterceptor;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class InvocationContextClientHttpRequestInterceptorTest {

    @Test
    void rebuildsStandardPropagationHeadersFromTheCurrentContext() throws Exception {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        final ContextState state = ContextState.of(
                new InvocationContext("10.0.0.8", "request-42"), new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
        final ContextPropagationInterceptor interceptor = new ContextPropagationInterceptor(
                threadLocalContextStore, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final HttpHeaders headers = new HttpHeaders();
        headers.set(InvocationContext.REQUEST_ID, "forged-request");
        headers.set(InvocationContext.ORIGIN_IP, "203.0.113.8");
        headers.set(AuthContext.PRINCIPAL_TYPE, "ADMIN");
        headers.set(AuthContext.PRINCIPAL_ID, "99");
        headers.set(AuthContext.PRINCIPAL_NAME, "mallory");
        final HttpRequest request = mock(HttpRequest.class);
        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        final ClientHttpResponse response = mock(ClientHttpResponse.class);
        final byte[] body = new byte[0];
        when(request.getHeaders()).thenReturn(headers);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("https://service.example/orders"));
        when(execution.execute(request, body)).thenReturn(response);

        new ContextManager(threadLocalContextStore, threadLocalContextStore).with(state).run(() -> {
            try {
                assertThat(interceptor.intercept(request, body, execution)).isSameAs(response);
            } catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        assertThat(headers.getFirst(InvocationContext.REQUEST_ID)).isEqualTo("request-42");
        assertThat(headers.getFirst(InvocationContext.ORIGIN_IP)).isEqualTo("10.0.0.8");
        assertThat(headers.getFirst(AuthContext.PRINCIPAL_TYPE)).isEqualTo("CUSTOMER");
        assertThat(headers.getFirst(AuthContext.PRINCIPAL_ID)).isEqualTo("7");
        assertThat(headers.getFirst(AuthContext.PRINCIPAL_NAME)).isEqualTo("alice");
        verify(execution).execute(request, body);
    }

    @Test
    void removesReservedHeadersWhenNoContextIsAvailable() throws Exception {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        final ContextPropagationInterceptor interceptor = new ContextPropagationInterceptor(
                threadLocalContextStore, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final HttpHeaders headers = new HttpHeaders();
        headers.set(InvocationContext.REQUEST_ID, "forged-request");
        headers.set(InvocationContext.ORIGIN_IP, "203.0.113.8");
        headers.set(AuthContext.PRINCIPAL_TYPE, "ADMIN");
        headers.set(AuthContext.PRINCIPAL_ID, "99");
        headers.set(AuthContext.PRINCIPAL_NAME, "mallory");
        final HttpRequest request = mock(HttpRequest.class);
        final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        final ClientHttpResponse response = mock(ClientHttpResponse.class);
        final byte[] body = new byte[0];
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(request, body)).thenReturn(response);

        assertThat(interceptor.intercept(request, body, execution)).isSameAs(response);

        assertThat(headers)
                .doesNotContainKey(InvocationContext.REQUEST_ID)
                .doesNotContainKey(InvocationContext.ORIGIN_IP)
                .doesNotContainKey(AuthContext.PRINCIPAL_TYPE)
                .doesNotContainKey(AuthContext.PRINCIPAL_ID)
                .doesNotContainKey(AuthContext.PRINCIPAL_NAME);
    }
}
