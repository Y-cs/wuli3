package com.kjs.wuli3.web.internal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedHttpAuthContextResolverTest {

    private final TrustedHttpAuthContextResolver resolver = new TrustedHttpAuthContextResolver();

    @Test
    void resolvesAuthenticationContextFromTrustedHeaders() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader(AuthContext.PRINCIPAL_TYPE, "CUSTOMER");
        request.addHeader(AuthContext.PRINCIPAL_ID, "42");
        request.addHeader(AuthContext.PRINCIPAL_NAME, "alice");

        assertThat(this.resolver.resolve(request)).contains(new AuthContext(PrincipalType.CUSTOMER, "42", "alice"));
    }

    @Test
    void returnsEmptyWhenAuthenticationHeadersAreAbsent() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");

        assertThat(this.resolver.resolve(request)).isEmpty();
    }

    @Test
    void returnsEmptyWhenAnyAuthenticationHeaderIsMissing() {
        for (final String field :
                new String[] {AuthContext.PRINCIPAL_TYPE, AuthContext.PRINCIPAL_ID, AuthContext.PRINCIPAL_NAME}) {
            final MockHttpServletRequest request = TrustedHttpAuthContextResolverTest.validRequest();
            request.removeHeader(field);

            assertThat(this.resolver.resolve(request)).as("缺少字段 %s", field).isEmpty();
        }
    }

    @Test
    void rejectsUnknownOrNonCanonicalPrincipalType() {
        for (final String principalType : new String[] {"UNKNOWN", "customer", " CUSTOMER "}) {
            final MockHttpServletRequest request = TrustedHttpAuthContextResolverTest.validRequest();
            request.removeHeader(AuthContext.PRINCIPAL_TYPE);
            request.addHeader(AuthContext.PRINCIPAL_TYPE, principalType);

            assertThatThrownBy(() -> this.resolver.resolve(request))
                    .as("非法主体类型 %s", principalType)
                    .isInstanceOf(ErrorCodeException.class);
        }
    }

    @Test
    void rejectsBlankAuthenticationHeadersWhenAllFieldsArePresent() {
        for (final String field :
                new String[] {AuthContext.PRINCIPAL_TYPE, AuthContext.PRINCIPAL_ID, AuthContext.PRINCIPAL_NAME}) {
            for (final String blank : new String[] {"", " ", "\t"}) {
                final MockHttpServletRequest request = TrustedHttpAuthContextResolverTest.validRequest();
                request.removeHeader(field);
                request.addHeader(field, blank);

                assertThatThrownBy(() -> this.resolver.resolve(request))
                        .as("空白字段 %s", field)
                        .isInstanceOf(ErrorCodeException.class);
            }
        }
    }

    /** 构造字段完整的可信认证请求，供各协议边界用例独立修改。 */
    private static MockHttpServletRequest validRequest() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader(AuthContext.PRINCIPAL_TYPE, "CUSTOMER");
        request.addHeader(AuthContext.PRINCIPAL_ID, "42");
        request.addHeader(AuthContext.PRINCIPAL_NAME, "alice");
        return request;
    }
}
