package com.kjs.wuli3.http.internal.logging;

import com.kjs.wuli3.http.logging.HttpLogOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 旁路复制单侧正文的有界字节，在终结时生成脱敏快照。
 *
 * 注意：仅供模块内部使用；不订阅、不持有或释放 DataBuffer，也不改变其读位置。
 *
 * @author 国杨 create on 2026/11/16 11:33
 */
public final class HttpBodyCapture {

    private enum State {
        UNAVAILABLE,
        INCOMPLETE,
        COMPLETE,
        TOO_LARGE,
        UNSUPPORTED,
        INVALID
    }

    private final HttpLogOptions options;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private @Nullable MediaType contentType;
    private State state = State.UNAVAILABLE;
    private boolean closed;

    /** 创建单次订阅独占的采集器；配置由客户端构建时校验。 */
    public HttpBodyCapture(final HttpLogOptions options) {
        this.options = options;
    }

    /** 根据最终内容类型开始采集；文件、流式、二进制及无法安全脱敏的文本不采集。 */
    public synchronized void start(final HttpHeaders headers) {
        if (this.closed || this.state != State.UNAVAILABLE) {
            return;
        }
        try {
            final MediaType type = headers.getContentType();
            this.contentType = type;
            final String encoding = headers.getFirst(HttpHeaders.CONTENT_ENCODING);
            final boolean supported = type == null
                    || type.getSubtype().equals("json")
                    || type.getSubtype().endsWith("+json")
                    || MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(type)
                    || (!this.options.redact()
                            && type.getType().equals("text")
                            && !MediaType.TEXT_EVENT_STREAM.equalsTypeAndSubtype(type));
            this.state = supported && (encoding == null || encoding.equalsIgnoreCase("identity"))
                    ? State.INCOMPLETE
                    : State.UNSUPPORTED;
        } catch (final RuntimeException exception) {
            this.state = State.INVALID;
        }
    }

    /** 有界复制当前缓冲区；超限或采集失败时丢弃已缓存正文，HTTP 信号保持原样。 */
    public synchronized void append(final DataBuffer buffer) {
        if (this.closed || this.state != State.INCOMPLETE) {
            return;
        }
        try {
            if (this.contentType == null && buffer.readableByteCount() > 0) {
                this.state = State.UNSUPPORTED;
            } else if (buffer.readableByteCount() > this.options.maxBodyBytes() - this.bytes.size()) {
                this.state = State.TOO_LARGE;
                this.bytes.reset();
            } else {
                try (final var buffers = buffer.readableByteBuffers()) {
                    while (buffers.hasNext()) {
                        final ByteBuffer source = buffers.next();
                        final byte[] part = new byte[source.remaining()];
                        source.get(part);
                        this.bytes.write(part, 0, part.length);
                    }
                }
            }
        } catch (final RuntimeException exception) {
            this.state = State.INVALID;
            this.bytes.reset();
        }
    }

    /** 仅在正文自然结束时标记完整；错误或取消保留不完整状态。 */
    public synchronized void complete() {
        if (!this.closed && this.state == State.INCOMPLETE) {
            this.state = State.COMPLETE;
        }
    }

    /** 冻结采集数据并添加正文及状态；不完整或脱敏失败时仅输出状态。 */
    public synchronized void writeTo(final Map<String, Object> fields, final String name) {
        this.closed = true;
        if (this.state == State.COMPLETE) {
            try {
                fields.put(
                        name,
                        this.bytes.size() == 0
                                ? ""
                                : HttpLogs.body(
                                        this.bytes.toByteArray(),
                                        this.contentType == null
                                                ? MediaType.APPLICATION_OCTET_STREAM
                                                : this.contentType,
                                        this.options));
            } catch (final IOException | RuntimeException exception) {
                this.state = State.INVALID;
            }
        }
        fields.put(name + "State", this.state.name().toLowerCase(Locale.ROOT));
    }

    /** 装饰实际发送的正文；编码器仍使用原生连接器与编解码配置。 */
    public ClientRequest wrapRequest(final ClientRequest request) {
        return ClientRequest.from(request)
                .body((output, context) -> {
                    final ClientHttpRequest decorated = output instanceof ZeroCopyHttpOutputMessage zeroCopy
                            ? new ZeroCopyRequest(output, this, zeroCopy)
                            : new CapturingRequest(output, this);
                    return request.body().insert(decorated, context).doOnSuccess(ignored -> {
                        this.start(output.getHeaders());
                        this.complete();
                    });
                })
                .build();
    }

    private synchronized void skip() {
        if (!this.closed) {
            this.state = State.UNSUPPORTED;
            this.bytes.reset();
        }
    }

    private Publisher<? extends DataBuffer> observe(final Publisher<? extends DataBuffer> body) {
        // 保留 Mono 类型，避免日志装饰将原生单块写入改为分块传输。
        return body instanceof Mono<?>
                ? Mono.from(body).doOnNext(this::append)
                : Flux.from(body).doOnNext(this::append);
    }

    private static class CapturingRequest extends ClientHttpRequestDecorator {
        final HttpBodyCapture capture;

        CapturingRequest(final ClientHttpRequest delegate, final HttpBodyCapture capture) {
            super(delegate);
            this.capture = capture;
        }

        @Override
        public Mono<Void> writeWith(final Publisher<? extends DataBuffer> body) {
            this.capture.start(this.getHeaders());
            return super.writeWith(this.capture.observe(body));
        }

        @Override
        public Mono<Void> writeAndFlushWith(final Publisher<? extends Publisher<? extends DataBuffer>> body) {
            this.capture.start(this.getHeaders());
            return super.writeAndFlushWith(Flux.from(body).map(this.capture::observe));
        }
    }

    private static final class ZeroCopyRequest extends CapturingRequest implements ZeroCopyHttpOutputMessage {
        private final ZeroCopyHttpOutputMessage delegate;

        ZeroCopyRequest(
                final ClientHttpRequest request,
                final HttpBodyCapture capture,
                final ZeroCopyHttpOutputMessage delegate) {
            super(request, capture);
            this.delegate = delegate;
        }

        @Override
        public Mono<Void> writeWith(final Path file, final long position, final long count) {
            this.capture.skip();
            return this.delegate.writeWith(file, position, count);
        }
    }
}
