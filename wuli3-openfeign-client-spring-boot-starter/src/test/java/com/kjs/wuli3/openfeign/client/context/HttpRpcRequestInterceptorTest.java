package com.kjs.wuli3.openfeign.client.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证出站上下文快照的编码、保留头清理、大小限制和本地作用域保持。
 *
 * @author 国杨 create on 2026/11/5 18:00
 */
class HttpRpcRequestInterceptorTest {
    @Test
    void replacesReservedHeadersAndDoesNotLeakContextBetweenCalls() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final HttpRpcRequestInterceptor interceptor = new HttpRpcRequestInterceptor(
                manager, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final RequestTemplate template = new RequestTemplate();
        template.header("x-wuli3-unknown", "forged");
        template.header("x-wuli3-request-id", "forged");
        template.header("Business", "kept");
        manager.with(new InvocationContext("127.0.0.1", "request-id")).run(() -> interceptor.apply(template));
        assertThat(template.headers()).doesNotContainKey("x-wuli3-unknown");
        assertThat(template.headers().get(InvocationContext.REQUEST_ID)).containsExactly("cmVxdWVzdC1pZA");
        assertThat(template.headers().get(HttpRpcProtocol.VERSION_HEADER)).containsExactly("1");
        interceptor.apply(template);
        assertThat(template.headers()).doesNotContainKey(InvocationContext.REQUEST_ID);
        assertThat(template.headers().get("Business")).containsExactly("kept");
    }

    @Test
    void rejectsHeaderInjection() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final HttpRpcRequestInterceptor interceptor = new HttpRpcRequestInterceptor(
                manager, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        assertThatThrownBy(() -> manager.with(new InvocationContext("127.0.0.1", "id\r\nInjected: yes"))
                        .run(() -> interceptor.apply(new RequestTemplate())))
                .isInstanceOf(ErrorCodeException.class);
    }

    /** 出站只捕获当前作用域，中文编码后可传输，且不会更改本地身份或业务认证头。 */
    @Test
    void propagatesUnicodeWithoutChangingTheCallingScope() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final HttpRpcRequestInterceptor interceptor = new HttpRpcRequestInterceptor(
                manager, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final InvocationContext invocation = new InvocationContext("127.0.0.1", "中文请求标识");
        final RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "service-credential");
        manager.with(invocation).run(() -> {
            final var original = manager.state();
            interceptor.apply(template);
            assertThat(manager.state()).isEqualTo(original);
        });
        assertThat(template.headers().get(InvocationContext.REQUEST_ID))
                .singleElement()
                .satisfies(encoded -> assertThat(
                                new String(java.util.Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8))
                        .isEqualTo("中文请求标识"));
        assertThat(template.headers().get("Authorization")).containsExactly("service-credential");
    }

    /** 字符数合法但 UTF-8/Base64URL 编码后超限的字段也必须在发出请求前被拒绝。 */
    @Test
    void rejectsRawAndEncodedOversizedContextHeaders() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final HttpRpcRequestInterceptor interceptor = new HttpRpcRequestInterceptor(
                manager, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        for (final String requestId : List.of("r".repeat(HttpRpcProtocol.MAX_HEADER_LENGTH + 1), "中".repeat(1100))) {
            assertThatThrownBy(() -> manager.with(new InvocationContext("127.0.0.1", requestId))
                            .run(() -> interceptor.apply(new RequestTemplate())))
                    .isInstanceOf(ErrorCodeException.class);
        }
    }
}
