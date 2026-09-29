package com.kjs.wuli3.propagation.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.Test;

/**
 * 验证上下文状态的覆盖、不可变更新和传播分区语义。
 *
 * @author 国杨 create on 2026/9/29 11:00
 */
class ContextStateTest {
    @Test
    void ofKeepsLastValueForEachKeyAcrossBothPartitions() {
        final AuthContext first = new AuthContext(PrincipalType.CUSTOMER, "1", "first");
        final AuthContext last = new AuthContext(PrincipalType.ADMIN, "2", "last");
        final LocalContext local = new LocalContext("last");
        final ContextState state = ContextState.of(first, new LocalContext("first"), last, local);

        assertThat(state.values()).containsExactlyInAnyOrder(last, local);
        assertThat(state.get(ContextKey.of(AuthContext.class))).contains(last);
        assertThat(state.get(ContextKey.of(LocalContext.class))).contains(local);
        assertThat(ContextSnapshot.from(state).values()).containsExactly(last);
    }

    @Test
    void updatesDoNotModifyOriginalStateOrCapturedSnapshot() {
        final AuthContext first = new AuthContext(PrincipalType.CUSTOMER, "1", "first");
        final AuthContext last = new AuthContext(PrincipalType.ADMIN, "2", "last");
        final LocalContext local = new LocalContext("local");
        final LocalContext replacement = new LocalContext("replacement");
        final ContextState original = ContextState.of(first, local);
        final ContextSnapshot snapshot = ContextSnapshot.from(original);
        final ContextState updated = original.with(last).with(replacement);

        assertThat(original.values()).containsExactlyInAnyOrder(first, local);
        assertThat(snapshot.values()).containsExactly(first);
        assertThat(updated.values()).containsExactlyInAnyOrder(last, replacement);
        assertThat(ContextSnapshot.from(updated).values()).containsExactly(last);
        assertThat(updated.without(ContextKey.of(AuthContext.class)).values()).containsExactly(replacement);
        assertThat(updated.without(ContextKey.of(LocalContext.class)).values()).containsExactly(last);
        assertThat(updated.without(ContextKey.of(AuthContext.class))
                .without(ContextKey.of(LocalContext.class))).isSameAs(ContextState.empty());
        assertThat(ContextState.of().without(ContextKey.of(LocalContext.class))).isSameAs(ContextState.empty());
        assertThatThrownBy(updated.values()::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(updated.propagationValues()::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    private record LocalContext(String value) implements Context {
        @Override
        public ContextKey<LocalContext> contentKey() {
            return ContextKey.of(LocalContext.class);
        }
    }
}
