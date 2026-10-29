package com.kjs.wuli3.propagation.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.Test;

class AuthContextTest {

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullIdentityFields() {
        assertThatThrownBy(() -> new AuthContext(null, "7")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, null)).isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void rejectsBlankPrincipalId() {
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, " ")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, "\t")).isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void allowsMissingCodeButRejectsBlankCode() {
        assertThat(new AuthContext(PrincipalType.CUSTOMER, "7").principalPermissionToken())
                .isNull();
        assertThat(new AuthContext(PrincipalType.CUSTOMER, "7", null).principalPermissionToken())
                .isNull();
        for (final String blank : new String[] {"", " ", "\t"}) {
            assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, "7", blank))
                    .isInstanceOf(ErrorCodeException.class);
        }
    }
}
