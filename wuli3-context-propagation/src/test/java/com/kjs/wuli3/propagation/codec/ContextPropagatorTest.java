package com.kjs.wuli3.propagation.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.propagation.context.ContextKey;
import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextPropagatorTest {

    @Test
    void standardEncoderWritesInvocationAndAuthenticationContexts() {
        final ContextPropagator encoder = new ContextPropagator(ContextPropagator.standardContextEncoder());
        final ContextSnapshot snapshot = ContextSnapshot.of(
                new InvocationContext("10.0.0.8", "request-42"), new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
        final Map<String, String> fields = new LinkedHashMap<>();

        encoder.inject(snapshot, fields::put);

        assertThat(fields)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        InvocationContext.REQUEST_ID, "request-42",
                        InvocationContext.ORIGIN_IP, "10.0.0.8",
                        AuthContext.PRINCIPAL_TYPE, "CUSTOMER",
                        AuthContext.PRINCIPAL_ID, "7",
                        AuthContext.PRINCIPAL_NAME, "alice"));
        assertThat(encoder.reservedFieldNames())
                .containsExactlyInAnyOrder(
                        InvocationContext.REQUEST_ID,
                        InvocationContext.ORIGIN_IP,
                        AuthContext.PRINCIPAL_TYPE,
                        AuthContext.PRINCIPAL_ID,
                        AuthContext.PRINCIPAL_NAME);
    }

    @Test
    @SuppressWarnings("NullAway")
    void standardEncoderRoundTripsInvocationAndAuthenticationContexts() {
        final ContextPropagator encoder = new ContextPropagator(ContextPropagator.standardContextEncoder());
        final ContextSnapshot source = ContextSnapshot.of(
                new InvocationContext("10.0.0.8", "request-42"), new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
        final Map<String, String> fields = new LinkedHashMap<>();

        encoder.inject(source, fields::put);
        final ContextSnapshot decoded = encoder.extract(fields::get);

        assertThat(decoded.get(ContextKey.of(InvocationContext.class))).contains(new InvocationContext("10.0.0.8", "request-42"));
        assertThat(decoded.get(ContextKey.of(AuthContext.class))).contains(new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void decoderRejectsUnknownPrincipalType() {
        final ContextPropagator encoder = new ContextPropagator(ContextPropagator.standardContextEncoder());
        final Map<String, String> fields = Map.of(
                InvocationContext.REQUEST_ID, "request-42",
                AuthContext.PRINCIPAL_TYPE, "UNKNOWN",
                AuthContext.PRINCIPAL_ID, "7",
                AuthContext.PRINCIPAL_NAME, "alice");

        assertThatThrownBy(() -> encoder.extract(fields::get))
                .isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void customEncoderOnlyReadsWritesAndReservesConfiguredFields() {
        final ContextPropagator encoder = new ContextPropagator(List.of(new InvocationContext.Codec()));
        final ContextSnapshot source = ContextSnapshot.of(
                new InvocationContext("10.0.0.8", "request-42"), new AuthContext(PrincipalType.CUSTOMER, "7", "alice"));
        final Map<String, String> fields = new LinkedHashMap<>();

        encoder.inject(source, fields::put);

        assertThat(fields)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        InvocationContext.REQUEST_ID, "request-42",
                        InvocationContext.ORIGIN_IP, "10.0.0.8"));
        assertThat(encoder.reservedFieldNames())
                .containsExactlyInAnyOrder(InvocationContext.REQUEST_ID, InvocationContext.ORIGIN_IP);
    }
}
