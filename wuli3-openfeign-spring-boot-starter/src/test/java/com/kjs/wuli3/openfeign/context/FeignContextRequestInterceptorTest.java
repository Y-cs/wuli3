package com.kjs.wuli3.openfeign.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

/**
 * 验证 Feign 出站请求头按当前上下文重建。
 *
 * @author lipt create on 2026/10/29 18:21
 */
class FeignContextRequestInterceptorTest {

    /** 使用当前上下文覆盖伪造的保留字段，并丢弃未知的保留前缀字段。 */
    @Test
    void rebuildsStandardPropagationHeadersFromTheCurrentContext() {
        final ThreadLocalContextBackend contextStore = new ThreadLocalContextBackend();
        final ContextState state = ContextState.of(
                new InvocationContext("10.0.0.8", "request-42"),
                new AuthContext(PrincipalType.CUSTOMER, "7", "alice", "permissions-v2"));
        final FeignContextRequestInterceptor interceptor = new FeignContextRequestInterceptor(
                contextStore, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final RequestTemplate template = new RequestTemplate();
        template.header(InvocationContext.REQUEST_ID, "forged-request");
        template.header(InvocationContext.ORIGIN_IP, "203.0.113.8");
        template.header(AuthContext.PRINCIPAL_TYPE, "ADMIN");
        template.header(AuthContext.PRINCIPAL_ID, "99");
        template.header(AuthContext.PRINCIPAL_NAME, "mallory");
        template.header(AuthContext.PRINCIPAL_PERMISSION_TOKEN, "forged");
        template.header("x-wULi3-Future-Field", "forged");

        new ContextManager(contextStore, contextStore).with(state).run(() -> interceptor.apply(template));

        assertThat(template.headers().get(InvocationContext.REQUEST_ID)).containsExactly("request-42");
        assertThat(template.headers().get(InvocationContext.ORIGIN_IP)).containsExactly("10.0.0.8");
        assertThat(template.headers().get(AuthContext.PRINCIPAL_TYPE)).containsExactly("CUSTOMER");
        assertThat(template.headers().get(AuthContext.PRINCIPAL_ID)).containsExactly("7");
        assertThat(template.headers().get(AuthContext.PRINCIPAL_NAME)).containsExactly("alice");
        assertThat(template.headers().get(AuthContext.PRINCIPAL_PERMISSION_TOKEN))
                .containsExactly("permissions-v2");
        assertThat(template.headers()).doesNotContainKey("x-wULi3-Future-Field");
    }

    /** 无可用上下文时清空所有保留字段，不能把调用方伪造值带到下游。 */
    @Test
    void removesReservedHeadersWhenNoContextIsAvailable() {
        final ThreadLocalContextBackend contextStore = new ThreadLocalContextBackend();
        final FeignContextRequestInterceptor interceptor = new FeignContextRequestInterceptor(
                contextStore, new ContextPropagator(ContextPropagator.standardContextEncoder()));
        final RequestTemplate template = new RequestTemplate();
        template.header(InvocationContext.REQUEST_ID, "forged-request");
        template.header(InvocationContext.ORIGIN_IP, "203.0.113.8");
        template.header(AuthContext.PRINCIPAL_TYPE, "ADMIN");
        template.header(AuthContext.PRINCIPAL_ID, "99");
        template.header(AuthContext.PRINCIPAL_NAME, "mallory");
        template.header("x-wULi3-Future-Field", "forged");

        interceptor.apply(template);

        assertThat(template.headers())
                .doesNotContainKey(InvocationContext.REQUEST_ID)
                .doesNotContainKey(InvocationContext.ORIGIN_IP)
                .doesNotContainKey(AuthContext.PRINCIPAL_TYPE)
                .doesNotContainKey(AuthContext.PRINCIPAL_ID)
                .doesNotContainKey(AuthContext.PRINCIPAL_NAME)
                .doesNotContainKey("x-wULi3-Future-Field");
    }
}
