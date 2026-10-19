package com.kjs.wuli3.spring.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.propagation.context.ContextSnapshot;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 验证 requestId 只在上下文作用域内进入 MDC，并在退出时还原进入前的值。
 *
 * @author lipt create on 2026/10/16 18:56
 */
class RequestIdMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** 验证作用域内写入快照中的 requestId，退出后移除。 */
    @Test
    void writesRequestIdInsideScopeAndRemovesAfterwards() {
        try (final RequestIdMdc ignored = RequestIdMdc.open(RequestIdMdcTest.snapshot("request-42"))) {
            assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("request-42");
        }
        assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isNull();
    }

    /** 验证退出时还原进入前的值，而不是无条件移除。 */
    @Test
    void restoresPreviousValueOnExit() {
        MDC.put(LogMdcKeys.REQUEST_ID, "outer");
        try (final RequestIdMdc ignored = RequestIdMdc.open(RequestIdMdcTest.snapshot("request-42"))) {
            assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("request-42");
        }
        assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("outer");
    }

    /** 验证快照中没有请求上下文时不写 MDC，也不影响已有值。 */
    @Test
    void skipsWhenSnapshotHasNoInvocationContext() {
        MDC.put(LogMdcKeys.REQUEST_ID, "outer");
        final ContextSnapshot snapshot =
                ContextSnapshot.of(new AuthContext(PrincipalType.CUSTOMER, "7", "alice", "permissions-v2"));
        try (final RequestIdMdc ignored = RequestIdMdc.open(snapshot)) {
            assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("outer");
        }
        assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("outer");
    }

    /** 验证回调抛异常时仍然还原。 */
    @Test
    void restoresWhenTaskFails() {
        assertThatThrownBy(() -> {
                    try (final RequestIdMdc ignored = RequestIdMdc.open(RequestIdMdcTest.snapshot("request-42"))) {
                        throw new IllegalStateException("boom");
                    }
                })
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isNull();
    }

    private static ContextSnapshot snapshot(final String requestId) {
        return ContextSnapshot.of(new InvocationContext("10.0.0.8", requestId));
    }
}
