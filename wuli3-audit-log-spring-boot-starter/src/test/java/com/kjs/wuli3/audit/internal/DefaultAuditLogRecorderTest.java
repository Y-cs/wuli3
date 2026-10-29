package com.kjs.wuli3.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.audit.payload.AuditLog;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import org.junit.jupiter.api.Test;

/**
 * 验证审计记录保留操作主体身份并支持缺少认证上下文的调用。
 *
 * @author 国杨 create on 2026/10/27 14:00
 */
class DefaultAuditLogRecorderTest {

    @Test
    void capturesPrincipalIdentityFromAuthenticationContext() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final RecordingEventPublisher publisher = new RecordingEventPublisher();
        final DefaultAuditLogRecorder recorder = new DefaultAuditLogRecorder("orders", publisher, backend, null);

        manager.with(new AuthContext(PrincipalType.CUSTOMER, "7", "permissions-v2"))
                .run(() -> recorder.record(AuditLog.success("ORDER", "order-1", "CREATE", "created")));

        assertThat(publisher.envelopes())
                .singleElement()
                .satisfies(event -> assertThat(event.payload().runtimeSnapshot().operator())
                        .extracting("principalType", "principalId")
                        .containsExactly(PrincipalType.CUSTOMER, "7"));
    }

    @Test
    void recordsWithoutPrincipalWhenAuthenticationContextIsAbsent() {
        final RecordingEventPublisher publisher = new RecordingEventPublisher();
        final DefaultAuditLogRecorder recorder =
                new DefaultAuditLogRecorder("orders", publisher, new ThreadLocalContextBackend(), null);

        recorder.record(AuditLog.success("ORDER", "order-1", "CREATE", "created"));

        assertThat(publisher.envelopes())
                .singleElement()
                .satisfies(event ->
                        assertThat(event.payload().runtimeSnapshot().operator()).isNull());
    }
}
