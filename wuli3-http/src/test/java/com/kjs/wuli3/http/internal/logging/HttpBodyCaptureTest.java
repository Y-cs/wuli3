package com.kjs.wuli3.http.internal.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.http.logging.HttpLogItem;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 验证有界正文采集、字符分片、自动脱敏与不完整正文的省略行为。
 *
 * @author 国杨 create on 2026/11/16 11:33
 */
class HttpBodyCaptureTest {

    @Test
    void copiesSplitUtf8WithoutChangingTheReadPositionAndRedactsNestedJson() {
        final HttpBodyCapture capture = this.capture(8192);
        capture.start(this.headers(MediaType.APPLICATION_JSON));
        final byte[] bytes = "{\"name\":\"中文\",\"nested\":[{\"token\":\"secret\"}]}".getBytes(StandardCharsets.UTF_8);
        for (final byte value : bytes) {
            final DataBuffer buffer = DefaultDataBufferFactory.sharedInstance.wrap(new byte[] {value});
            capture.append(buffer);
            assertThat(buffer.readPosition()).isZero();
        }
        capture.complete();
        assertThat(this.fields(capture)).containsEntry("bodyState", "complete");
        assertThat(this.fields(capture).get("body"))
                .asString()
                .contains("中文", "***")
                .doesNotContain("secret");
    }

    @Test
    void omitsOversizedIncompleteAndMalformedJsonInsteadOfLoggingRawPrefixes() {
        final HttpBodyCapture oversized = this.capture(4);
        oversized.start(this.headers(MediaType.APPLICATION_JSON));
        oversized.append(DefaultDataBufferFactory.sharedInstance.wrap(
                "{\"token\":\"secret\"}".getBytes(StandardCharsets.UTF_8)));
        oversized.complete();
        assertThat(this.fields(oversized))
                .containsEntry("bodyState", "too_large")
                .doesNotContainKey("body");
        final HttpBodyCapture incomplete = this.capture(8192);
        incomplete.start(this.headers(MediaType.APPLICATION_JSON));
        incomplete.append(DefaultDataBufferFactory.sharedInstance.wrap(
                "{\"token\":\"secret\"}".getBytes(StandardCharsets.UTF_8)));
        assertThat(this.fields(incomplete))
                .containsEntry("bodyState", "incomplete")
                .doesNotContainKey("body");
        for (final String body : new String[] {
            " ", "{\"token\":\"secret\"", "{} {\"token\":\"secret\"}", "{\"token\":\"secret\"} trailing"
        }) {
            final HttpBodyCapture malformed = this.capture(8192);
            malformed.start(this.headers(MediaType.APPLICATION_JSON));
            malformed.append(DefaultDataBufferFactory.sharedInstance.wrap(body.getBytes(StandardCharsets.UTF_8)));
            malformed.complete();
            assertThat(this.fields(malformed))
                    .containsEntry("bodyState", "invalid")
                    .doesNotContainKey("body");
        }
    }

    @Test
    void skipsStreamingBinaryAndUnparseableTextWhenRedacting() {
        for (final MediaType type :
                new MediaType[] {MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN
                }) {
            final HttpBodyCapture capture = this.capture(8192);
            capture.start(this.headers(type));
            capture.append(DefaultDataBufferFactory.sharedInstance.wrap("private".getBytes(StandardCharsets.UTF_8)));
            capture.complete();
            assertThat(this.fields(capture))
                    .containsEntry("bodyState", "unsupported")
                    .doesNotContainKey("body");
        }
    }

    @Test
    void supportsEmptyBodiesAndExplicitUnredactedText() {
        final HttpBodyCapture empty = this.capture(8192);
        empty.start(new HttpHeaders());
        empty.complete();
        assertThat(this.fields(empty)).containsEntry("body", "").containsEntry("bodyState", "complete");
        final HttpBodyCapture text =
                new HttpBodyCapture(new HttpLogOptions(true, false, Set.of(), Set.of(HttpLogItem.RESPONSE_BODY), 8192));
        text.start(this.headers(MediaType.TEXT_PLAIN));
        text.append(DefaultDataBufferFactory.sharedInstance.wrap("visible".getBytes(StandardCharsets.UTF_8)));
        text.complete();
        assertThat(this.fields(text)).containsEntry("body", "visible");
    }

    private HttpBodyCapture capture(final int limit) {
        return new HttpBodyCapture(new HttpLogOptions(true, true, Set.of(), Set.of(HttpLogItem.RESPONSE_BODY), limit));
    }

    private HttpHeaders headers(final MediaType type) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        return headers;
    }

    private Map<String, Object> fields(final HttpBodyCapture capture) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        capture.writeTo(fields, "body");
        return fields;
    }
}
