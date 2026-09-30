package com.kjs.wuli3.propagation.context;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.Test;

class AuthContextTest {

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullFields() {
        assertThatThrownBy(() -> new AuthContext(null, "7", "alice")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, null, "alice"))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, "7", null))
                .isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void rejectsBlankTextFields() {
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, " ", "alice"))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new AuthContext(PrincipalType.CUSTOMER, "7", "\t"))
                .isInstanceOf(ErrorCodeException.class);
    }
}
