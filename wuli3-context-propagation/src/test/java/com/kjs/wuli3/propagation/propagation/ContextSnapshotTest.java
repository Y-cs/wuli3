package com.kjs.wuli3.propagation.propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.Test;

class ContextSnapshotTest {

    @Test
    void getReturnsEmptyWhenTypeIsAbsent() {
        final ContextSnapshot snapshot = ContextSnapshot.of(new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));

        assertThat(snapshot.get(ContextKey.of(InvocationContext.class))).isEmpty();
    }

    @Test
    void sameContextTypeUsesLastValueAndValuesCannotBeMutated() {
        final ContextSnapshot snapshot = ContextSnapshot.of(
                new AuthContext(PrincipalType.CUSTOMER, "7", "alice"),
                new AuthContext(PrincipalType.ADMIN, "8", "bob"));

        assertThat(snapshot.get(ContextKey.of(AuthContext.class))).contains(new AuthContext(PrincipalType.ADMIN, "8", "bob"));
        assertThatThrownBy(snapshot.values()::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
