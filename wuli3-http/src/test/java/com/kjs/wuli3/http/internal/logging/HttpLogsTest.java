package com.kjs.wuli3.http.internal.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kjs.wuli3.http.logging.HttpLogEvent;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 验证摘要地址脱敏、共享接收器故障隔离与日志开关。
 *
 * @author 国杨 create on 2026/11/13 17:49
 */
class HttpLogsTest {

    @Test
    void redactsEncodedBuiltInAndCustomQueryNamesAndStripsCredentialsAndFragments() {
        final HttpLogOptions options = new HttpLogOptions(true, true, Set.of("account-number"));
        final URI uri =
                URI.create("https://user:private@example.org/path?%61pi_key=key&account_number=42&normal=ok#private");
        assertThat(HttpLogs.url(uri, options))
                .isEqualTo("https://example.org/path?%61pi_key=***&account_number=***&normal=ok");
        assertThat(HttpLogs.url(URI.create("https://example.org/?access_token=private&signature=signed"), options))
                .isEqualTo("https://example.org/?access_token=***&signature=***");
    }

    @Test
    void redactionSwitchPreservesQueryButStillStripsCredentialsAndFragments() {
        final HttpLogOptions options = new HttpLogOptions(true, false, Set.of());
        assertThat(HttpLogs.url(URI.create("https://user:private@example.org/path?token=visible#private"), options))
                .isEqualTo("https://example.org/path?token=visible");
        assertThat(HttpLogs.url(URI.create("https://example.org/path"), options))
                .isEqualTo("https://example.org/path");
    }

    @Test
    void disabledLoggingDoesNotCallTheSharedRecorder() {
        final HttpLogRecorder recorder =
                HttpLogs.safeRecorder(new HttpLogOptions(false, true, Set.of()), (event, fields) -> {
                    throw new AssertionError("关闭日志时不应调用接收器");
                });
        recorder.record(HttpLogEvent.DIAGNOSTIC, Map.of());
    }

    @Test
    void recorderRuntimeFailuresAreIsolated() {
        final HttpLogRecorder recorder = HttpLogs.safeRecorder(HttpLogOptions.defaults(), (event, fields) -> {
            throw new IllegalStateException("record failed");
        });
        assertThatCode(() -> recorder.record(HttpLogEvent.DIAGNOSTIC, Map.of())).doesNotThrowAnyException();
    }
}
