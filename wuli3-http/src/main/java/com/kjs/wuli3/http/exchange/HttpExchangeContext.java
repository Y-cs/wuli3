package com.kjs.wuli3.http.exchange;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.http.internal.logging.HttpBodyCapture;
import com.kjs.wuli3.http.internal.logging.HttpLogs;
import com.kjs.wuli3.http.logging.HttpLogItem;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * 保存每次订阅独立的生命周期状态、共享数据和待汇总日志。
 *
 * 注意：回调可能切换线程；attributes 支持并发访问，其中存放的对象由调用方保证线程安全。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public final class HttpExchangeContext {

    private final String id = UUID.randomUUID().toString();
    private final String clientName;
    private final HttpLogOptions logging;
    private final long started = System.nanoTime();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile boolean delivered;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final @Nullable HttpBodyCapture requestBody;
    private final @Nullable HttpBodyCapture responseBody;
    private @Nullable List<Map<String, ?>> diagnostics;
    private boolean logsClosed;

    public HttpExchangeContext(final String clientName, final HttpLogOptions logging) {
        this.clientName = clientName;
        this.logging = logging;
        this.requestBody = logging.enabled() && logging.items().contains(HttpLogItem.REQUEST_BODY)
                ? new HttpBodyCapture(logging)
                : null;
        this.responseBody = logging.enabled() && logging.items().contains(HttpLogItem.RESPONSE_BODY)
                ? new HttpBodyCapture(logging)
                : null;
    }

    /** 返回本次订阅唯一的调用标识。 */
    public String id() {
        return this.id;
    }

    /** 返回配置中的客户端名称。 */
    public String clientName() {
        return this.clientName;
    }

    /**
     * 追加本次请求的结构化诊断条目，最终随唯一汇总日志按追加顺序输出。
     *
     * 注意：只复制 Map 本身，值应为不可变对象并由调用者脱敏；键值不得为 null。
     * 日志关闭或终结快照生成后不再暂存，错误回调在生成快照前仍可追加。
     */
    public synchronized void addLog(final Map<String, ?> entry) {
        Asserts.whenNull(entry).throwIllegalArgumentException("诊断日志不能为空");
        if (!this.logging.enabled() || this.logsClosed) {
            return;
        }
        entry.forEach((key, value) -> {
            Asserts.whenBlank(key).throwIllegalArgumentException("诊断日志字段名不能为空");
            Asserts.whenNull(value).throwIllegalArgumentException("诊断日志字段值不能为空");
        });
        if (this.diagnostics == null) {
            this.diagnostics = new ArrayList<>();
        }
        this.diagnostics.add(Map.copyOf(entry));
    }

    /** 返回回调间共享的诊断与业务临时数据。 */
    public Map<String, Object> attributes() {
        return this.attributes;
    }

    synchronized void request(final ClientRequest request) {
        if (this.logging.enabled() && !this.logsClosed) {
            this.fields.put("method", request.method().name());
            this.fields.put("url", HttpLogs.url(request.url(), this.logging));
            if (this.logging.items().contains(HttpLogItem.REQUEST_HEADERS)) {
                this.fields.put("requestHeaders", HttpLogs.headers(request.headers(), this.logging));
            }
        }
    }

    ClientRequest captureRequest(final ClientRequest request) {
        this.request(request);
        final ClientRequest captured = this.requestBody == null ? request : this.requestBody.wrapRequest(request);
        if (!this.logging.enabled() || !this.logging.items().contains(HttpLogItem.REQUEST_HEADERS)) {
            return captured;
        }
        // 编码器可能补充 Content-Type 等头部，提交前更新快照；不访问连接器的私有实现。
        return ClientRequest.from(captured)
                .httpRequest(output -> output.beforeCommit(() -> Mono.fromRunnable(() -> this.requestHeaders(output))))
                .build();
    }

    private synchronized void requestHeaders(final ClientHttpRequest request) {
        if (this.logsClosed) {
            return;
        }
        final HttpHeaders headers = new HttpHeaders();
        headers.addAll(request.getHeaders());
        if (!request.getCookies().isEmpty()) {
            headers.add(
                    HttpHeaders.COOKIE,
                    request.getCookies().values().stream()
                            .flatMap(List::stream)
                            .map(Object::toString)
                            .collect(java.util.stream.Collectors.joining("; ")));
        }
        this.fields.put("requestHeaders", HttpLogs.headers(headers, this.logging));
    }

    synchronized void response(final ClientResponse response) {
        if (this.logging.enabled() && !this.logsClosed) {
            this.fields.put("status", response.statusCode().value());
            if (this.logging.items().contains(HttpLogItem.RESPONSE_HEADERS)) {
                this.fields.put(
                        "responseHeaders", HttpLogs.headers(response.headers().asHttpHeaders(), this.logging));
            }
        }
    }

    Flux<DataBuffer> captureResponse(final Flux<DataBuffer> body, final HttpHeaders headers) {
        final HttpBodyCapture capture = this.responseBody;
        if (capture == null) {
            return body;
        }
        capture.start(headers);
        return body.doOnNext(capture::append).doOnComplete(capture::complete);
    }

    boolean tryFinish() {
        return this.finished.compareAndSet(false, true);
    }

    void markDelivered() {
        this.delivered = true;
    }

    boolean delivered() {
        return this.delivered;
    }

    synchronized Map<String, Object> finishLog(final SignalType signal, final @Nullable Throwable failure) {
        this.logsClosed = true;
        this.fields.put("client", this.clientName);
        this.fields.put("exchangeId", this.id);
        this.fields.put("outcome", failure != null ? "error" : signal == SignalType.CANCEL ? "cancelled" : "complete");
        this.fields.put("elapsedNanos", System.nanoTime() - this.started);
        if (failure != null) {
            this.fields.put("error", failure.getClass().getName());
        }
        if (this.requestBody != null) {
            this.requestBody.writeTo(this.fields, "requestBody");
        }
        if (this.responseBody != null) {
            this.responseBody.writeTo(this.fields, "responseBody");
        }
        if (this.diagnostics != null) {
            this.fields.put("diagnostics", List.copyOf(this.diagnostics));
        }
        return Map.copyOf(this.fields);
    }
}
