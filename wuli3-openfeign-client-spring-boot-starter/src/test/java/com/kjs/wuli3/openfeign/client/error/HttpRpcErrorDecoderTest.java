package com.kjs.wuli3.openfeign.client.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.propagation.ErrorCodePropagator;
import com.kjs.wuli3.json.provider.JacksonProvider;
import com.kjs.wuli3.openfeign.client.protocol.HttpRpcProtocol;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证内部错误的识别边界、有界读取及响应流释放，拒绝把传输失败推测为业务错误。
 *
 * @author 国杨 create on 2026/11/5 18:00
 */
class HttpRpcErrorDecoderTest {

    private final HttpRpcErrorDecoder decoder = new HttpRpcErrorDecoder();

    /** 协议解析失败与响应流截断保留诊断原因，仍属于 HTTP 传输失败。 */
    @Test
    void preservesMalformedAndUnreadableResponseCauses() {
        final Request request = Request.create(
                Request.HttpMethod.GET,
                "http://internal",
                Map.of(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate());
        final Exception malformed = new HttpRpcErrorDecoder()
                .decode(
                        "sample",
                        HttpRpcErrorDecoderTest.response()
                                .request(request)
                                .body("{".getBytes(StandardCharsets.UTF_8))
                                .build());
        assertThat(malformed).isInstanceOf(FeignException.class);
        assertThat(malformed.getSuppressed()).hasSize(1).allMatch(IOException.class::isInstance);
        final Response response = HttpRpcErrorDecoderTest.response()
                .request(request)
                .body(
                        new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("connection truncated");
                            }

                            @Override
                            public int read(final byte[] buffer, final int offset, final int length)
                                    throws IOException {
                                throw new IOException("connection truncated");
                            }
                        },
                        null)
                .build();
        final Exception unreadable = new HttpRpcErrorDecoder().decode("sample", response);
        assertThat(unreadable).isInstanceOf(FeignException.class);
        assertThat(unreadable.getSuppressed()).hasSize(1);
        assertThat(unreadable.getSuppressed()[0]).hasMessage("connection truncated");
    }

    /** 协议标记必须唯一且匹配，大小写和合法媒体类型参数不影响识别。 */
    @Test
    void requiresSingleMatchingProtocolMarkers() throws IOException {
        final byte[] body = HttpRpcErrorDecoderTest.document();
        final List<Map<String, Collection<String>>> invalid = List.of(
                Map.of(),
                Map.of(
                        HttpRpcProtocol.VERSION_HEADER,
                        List.of("2"),
                        "Content-Type",
                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE)),
                Map.of(
                        HttpRpcProtocol.VERSION_HEADER,
                        List.of("1", "1"),
                        "Content-Type",
                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE)),
                Map.of(HttpRpcProtocol.VERSION_HEADER, List.of("1"), "Content-Type", List.of("application/json")),
                Map.of(
                        HttpRpcProtocol.VERSION_HEADER,
                        List.of("1"),
                        "Content-Type",
                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE, HttpRpcProtocol.ERROR_CONTENT_TYPE)));
        for (final Map<String, Collection<String>> headers : invalid) {
            try (final Response response = HttpRpcErrorDecoderTest.response()
                    .headers(headers)
                    .body(body)
                    .build()) {
                assertThat(this.decoder.decode("sample", response)).isInstanceOf(FeignException.class);
            }
        }
        try (final Response response = HttpRpcErrorDecoderTest.response()
                .headers(Map.of(
                        "x-wuli3-rpc-version",
                        List.of("1"),
                        "content-type",
                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE + "; charset=UTF-8")))
                .body(body)
                .build()) {
            assertThat(this.decoder.decode("sample", response)).isInstanceOf(ErrorCodeException.class);
        }
    }

    /** 正好达到限制的有效文档可以恢复；超限只多读一个字节，并释放底层流。 */
    @Test
    void boundsReadsAndClosesStreamsAtTheSizeBoundary() throws IOException {
        final byte[] document = HttpRpcErrorDecoderTest.document();
        final byte[] exact = Arrays.copyOf(document, HttpRpcProtocol.MAX_ERROR_BYTES);
        Arrays.fill(exact, document.length, exact.length, (byte) ' ');
        final TrackingInputStream accepted = new TrackingInputStream(exact);
        final Exception error = this.decoder.decode(
                "sample",
                HttpRpcErrorDecoderTest.response().body(accepted, null).build());
        assertThat(error).isInstanceOf(ErrorCodeException.class);
        assertThat(accepted.bytesRead).isEqualTo(HttpRpcProtocol.MAX_ERROR_BYTES);
        assertThat(accepted.closed).isTrue();

        final TrackingInputStream rejected = new TrackingInputStream(new byte[HttpRpcProtocol.MAX_ERROR_BYTES * 2]);
        final Exception failure = this.decoder.decode(
                "sample",
                HttpRpcErrorDecoderTest.response().body(rejected, null).build());
        assertThat(failure).isInstanceOf(FeignException.class);
        assertThat(rejected.bytesRead).isEqualTo(HttpRpcProtocol.MAX_ERROR_BYTES + 1);
        assertThat(rejected.closed).isTrue();
    }

    /** 非内部正文保留在传输异常中，响应没有正文时也不尝试构造业务异常。 */
    @Test
    void preservesUnmarkedBodiesAndHandlesMissingBodies() {
        try (final Response response = HttpRpcErrorDecoderTest.response()
                .headers(Map.of())
                .body("gateway failure", StandardCharsets.UTF_8)
                .build()) {
            final Exception failure = this.decoder.decode("sample", response);
            assertThat(failure)
                    .isInstanceOfSatisfying(
                            FeignException.class,
                            error -> assertThat(error.contentUTF8()).isEqualTo("gateway failure"));
            assertThat(failure.getSuppressed()).isEmpty();
        }
        try (final Response response = HttpRpcErrorDecoderTest.response().build()) {
            assertThat(this.decoder.decode("sample", response)).isInstanceOf(FeignException.class);
        }
    }

    /** 独立构造完整传播字段，测试样本不依赖提供端实现。 */
    private static byte[] document() throws IOException {
        final ErrorCodeCarrier error = new ErrorCodeCarrier(
                "original",
                "code",
                "诊断消息",
                ErrorOrigin.SERVER,
                ErrorSeverity.CRITICAL,
                "source",
                ErrorVisibility.INTERNAL);
        final Map<String, String> fields = new LinkedHashMap<>();
        new ErrorCodePropagator().inject(error, fields::put);
        return JacksonProvider.newJsonMapper().writeValueAsBytes(Map.of("version", "1", "error", fields));
    }

    /** 默认构造标记完整的内部失败，具体用例可覆盖标记或正文。 */
    private static Response.Builder response() {
        return Response.builder()
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://internal",
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate()))
                .status(500)
                .headers(Map.of(
                        HttpRpcProtocol.VERSION_HEADER,
                        List.of(HttpRpcProtocol.VERSION),
                        "Content-Type",
                        List.of(HttpRpcProtocol.ERROR_CONTENT_TYPE)));
    }

    /** 记录边界上实际消费的字节及关闭状态，避免只验证内存样本长度。 */
    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private int bytesRead;
        private boolean closed;

        private TrackingInputStream(final byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            final int value = this.delegate.read();
            if (value >= 0) {
                this.bytesRead++;
            }
            return value;
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            final int count = this.delegate.read(bytes, offset, length);
            if (count > 0) {
                this.bytesRead += count;
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            this.delegate.close();
        }
    }
}
