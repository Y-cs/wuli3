package com.kjs.wuli3.rocket.internal;

import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.context.ContextState;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RocketContextSupportTest {

    @Test
    void decodesHeadersAndRestoresThePreviousContextAfterScope() throws Exception {
        final ThreadLocalContextBackend threadLocalContextStore = new ThreadLocalContextBackend();
        new ContextManager(threadLocalContextStore, threadLocalContextStore).with(ContextState.of(new InvocationContext("127.0.0.1", "previous"))).call(() -> {
            final RocketContextSupport support = new RocketContextSupport(
                    new ContextManager(threadLocalContextStore, threadLocalContextStore), new ContextPropagator(ContextPropagator.standardContextEncoder()));
            final Map<String, ?> headers = Map.of(
                    InvocationContext.REQUEST_ID, "request-42",
                    InvocationContext.ORIGIN_IP, "10.0.0.8",
                    AuthContext.PRINCIPAL_TYPE, "CUSTOMER",
                    AuthContext.PRINCIPAL_ID, "7",
                    AuthContext.PRINCIPAL_NAME, "alice");

            support.callInScope(headers, () -> {
                assertThat(threadLocalContextStore.get(ContextKey.of(InvocationContext.class)))
                        .contains(new InvocationContext("10.0.0.8", "request-42"));
                assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class)))
                        .contains(new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
                return null;
            });
            assertThat(threadLocalContextStore.get(ContextKey.of(InvocationContext.class)))
                    .contains(new InvocationContext("127.0.0.1", "previous"));
            assertThat(threadLocalContextStore.get(ContextKey.of(AuthContext.class))).isEmpty();
            return null;
        });
    }
}
