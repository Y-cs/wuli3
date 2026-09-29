package com.kjs.wuli3.propagation.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.context.Context;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ThreadLocalContextBackendTest {
    @Test
    void nestedScopesRestorePreviousState() {
        final ThreadLocalContextBackend store = new ThreadLocalContextBackend();
        final AuthContext first = new AuthContext(PrincipalType.CUSTOMER, "1", "first");
        final AuthContext second = new AuthContext(PrincipalType.ADMIN, "2", "second");
        final ContextManager manager = new ContextManager(store, store);
        manager.with(ContextState.of(first)).run(() -> {
            manager.with(ContextState.of(second))
                    .run(() -> assertThat(store.get(ContextKey.of(AuthContext.class)))
                            .contains(second));
            assertThat(store.get(ContextKey.of(AuthContext.class))).contains(first);
        });
        assertThat(store.get(ContextKey.of(AuthContext.class))).isEmpty();
    }

    @Test
    void propagationSnapshotExcludesLocalContext() {
        final ThreadLocalContextBackend store = new ThreadLocalContextBackend();
        final LocalContext local = new LocalContext("local");
        final AuthContext auth = new AuthContext(PrincipalType.CUSTOMER, "7", "alice");
        final ContextManager manager = new ContextManager(store, store);
        manager.with(ContextState.of(local, auth)).run(() -> {
            final ContextSnapshot snapshot = manager.capture();
            assertThat(snapshot.get(ContextKey.of(AuthContext.class))).contains(auth);
            assertThat(snapshot.get(ContextKey.of(LocalContext.class))).isEmpty();
        });
    }

    @Test
    void wrappedCallableUsesCapturedContextAndRestoresCallerContext() throws Exception {
        final ThreadLocalContextBackend store = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(store, store);
        final AuthContext captured = new AuthContext(PrincipalType.CUSTOMER, "1", "captured");
        final AuthContext caller = new AuthContext(PrincipalType.CUSTOMER, "2", "caller");
        manager.with(ContextState.of(captured)).run(() -> {
            final Callable<String> wrapped = manager.from(manager.capture())
                    .wrap(() -> store.get(ContextKey.of(AuthContext.class))
                            .orElseThrow()
                            .principalId());
            manager.with(ContextState.of(caller)).run(() -> {
                try {
                    assertThat(wrapped.call()).isEqualTo("1");
                } catch (final Exception exception) {
                    throw new RuntimeException(exception);
                }
                assertThat(store.get(ContextKey.of(AuthContext.class))).contains(caller);
            });
        });
    }

    private record LocalContext(String value) implements Context {
        @Override
        public ContextKey<LocalContext> contentKey() {
            return ContextKey.of(LocalContext.class);
        }
    }
}
