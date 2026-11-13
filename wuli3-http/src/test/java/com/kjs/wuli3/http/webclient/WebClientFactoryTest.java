package com.kjs.wuli3.http.webclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.http.logging.HttpLogEvent;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import io.netty.buffer.PooledByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * 验证无容器客户端、订阅隔离、生命周期错误及正文所有权。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
class WebClientFactoryTest {

    private DisposableServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        this.server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post(
                                "/echo",
                                (request, response) -> request.receive()
                                        .aggregate()
                                        .asString()
                                        .flatMap(body -> response.header(
                                                        HttpHeaders.CONTENT_TYPE,
                                                        request.requestHeaders().get(HttpHeaders.CONTENT_TYPE))
                                                .header(
                                                        "X-Signed",
                                                        request.requestHeaders().get("X-Signed", "none"))
                                                .header(
                                                        "X-Body-Framing",
                                                        request.requestHeaders()
                                                                .get(
                                                                        HttpHeaders.TRANSFER_ENCODING,
                                                                        request.requestHeaders()
                                                                                .get(
                                                                                        HttpHeaders.CONTENT_LENGTH,
                                                                                        "none")))
                                                .sendString(Mono.just(body))
                                                .then()))
                        .get(
                                "/json",
                                (request, response) -> response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                        .sendString(Mono.just("{\"message\":\"hello\",\"token\":\"response-secret\"}")))
                        .get(
                                "/status",
                                (request, response) -> response.status(400).sendString(Mono.just("bad request")))
                        .get(
                                "/redirect",
                                (request, response) -> response.status(302)
                                        .header("Location", "/json")
                                        .send())
                        .get(
                                "/slow",
                                (request, response) -> Mono.delay(Duration.ofSeconds(2))
                                        .then(response.send().then())))
                .bindNow();
        this.baseUrl = "http://127.0.0.1:" + this.server.port();
    }

    @AfterEach
    void stopServer() {
        this.server.disposeNow();
    }

    @Test
    void initializesOnceAndRunsOrderedHooksOnlyOnSubscription() throws Exception {
        final List<String> order = new CopyOnWriteArrayList<>();
        final Logs logs = new Logs();
        final AtomicReference<HttpExchangeContext> context = new AtomicReference<>();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> {
                    order.add("initialize-1");
                    initialization.builder().defaultHeader("X-App", "orders");
                })
                .initialize(initialization -> order.add("initialize-2"))
                .beforeRequest((exchange, request) -> {
                    order.add("before-1");
                    assertThat(request.headers().getFirst("X-App")).isEqualTo("orders");
                    context.set(exchange);
                    exchange.attributes().put("value", "shared");
                    exchange.logRecorder().record(HttpLogEvent.DIAGNOSTIC, Map.of("exchangeId", exchange.id()));
                    return Mono.just(ClientRequest.from(request)
                            .header("X-Signed", "yes")
                            .build());
                })
                .beforeRequest((exchange, request) -> {
                    order.add("before-2");
                    assertThat(exchange).isSameAs(context.get());
                    return Mono.just(request);
                })
                .afterResponse((exchange, response) -> {
                    order.add("after-1");
                    assertThat(exchange).isSameAs(context.get());
                    assertThat(exchange.attributes()).containsEntry("value", "shared");
                    assertThat(response.headers().header("X-Signed")).containsExactly("yes");
                    return Mono.just(response);
                })
                .afterResponse((exchange, response) -> {
                    order.add("after-2");
                    return Mono.just(response);
                })
                .logRecorder(logs)
                .build();
        final Mono<String> result = client.post()
                .uri("/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"hello\"}")
                .retrieve()
                .bodyToMono(String.class);
        assertThat(order).containsExactly("initialize-1", "initialize-2");
        assertThat(result.block(Duration.ofSeconds(5))).isEqualTo("{\"message\":\"hello\"}");
        logs.await(1);
        assertThat(order).containsExactly("initialize-1", "initialize-2", "before-1", "before-2", "after-1", "after-2");
        assertThat(logs.events(HttpLogEvent.DIAGNOSTIC))
                .singleElement()
                .satisfies(event -> assertThat(event.fields().get("exchangeId"))
                        .isEqualTo(context.get().id()));
        assertThat(logs.events(HttpLogEvent.COMPLETE))
                .singleElement()
                .satisfies(event -> assertThat(event.fields().get("outcome")).isEqualTo("complete"));
    }

    @Test
    void isolatesContextsAndSharesOneRecorderAcrossResubscriptionAndConcurrentCalls() throws Exception {
        final Logs logs = new Logs();
        final List<HttpExchangeContext> contexts = new CopyOnWriteArrayList<>();
        final AtomicReference<HttpLogRecorder> initializedRecorder = new AtomicReference<>();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initializedRecorder.set(initialization.logRecorder()))
                .beforeRequest((context, request) -> {
                    context.attributes().put("requestId", context.id());
                    contexts.add(context);
                    return Mono.just(request);
                })
                .afterResponse((context, response) -> {
                    assertThat(context.attributes()).containsEntry("requestId", context.id());
                    return Mono.just(response);
                })
                .logRecorder(logs)
                .build();
        final Mono<String> result = client.get().uri("/json").retrieve().bodyToMono(String.class);
        result.block(Duration.ofSeconds(5));
        result.block(Duration.ofSeconds(5));
        assertThat(Flux.range(0, 8).flatMap(index -> result).collectList().block(Duration.ofSeconds(5)))
                .hasSize(8);
        logs.await(10);
        assertThat(contexts).hasSize(10);
        assertThat(contexts.stream().map(HttpExchangeContext::id).distinct()).hasSize(10);
        assertThat(contexts).allSatisfy(context -> {
            assertThat(context.logRecorder()).isSameAs(initializedRecorder.get());
            assertThat(logs.entries.stream()
                            .filter(event -> context.id().equals(event.fields().get("exchangeId"))))
                    .extracting(Event::name)
                    .containsExactly(HttpLogEvent.REQUEST, HttpLogEvent.RESPONSE, HttpLogEvent.COMPLETE);
        });
        assertThat(logs.events(HttpLogEvent.INITIALIZED)).hasSize(1);
    }

    @Test
    void transformsBodyWithoutChangingNativeDecoding() throws Exception {
        final Logs logs = new Logs();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .afterResponse((context, response) -> DataBufferUtils.join(response.bodyToFlux(DataBuffer.class), 1024)
                        .map(buffer -> {
                            try {
                                final String source = buffer.toString(StandardCharsets.UTF_8);
                                return response.mutate()
                                        .body(ignored -> Flux.just(source.replace("hello", "decrypted"))
                                                .map(value -> DefaultDataBufferFactory.sharedInstance.wrap(
                                                        value.getBytes(StandardCharsets.UTF_8))))
                                        .build();
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        }))
                .logRecorder(logs)
                .build();
        final Map<?, ?> value = client.get()
                .uri("/json")
                .retrieve()
                .bodyToMono(Map.class)
                .blockOptional(Duration.ofSeconds(5))
                .orElseThrow();
        assertThat(value.get("message")).isEqualTo("decrypted");
        logs.await(1);
        final String log = logs.events(HttpLogEvent.COMPLETE).toString();
        assertThat(log).doesNotContain("hello", "decrypted", "response-secret");
    }

    @Test
    void keepsStatusConversionAndDecoderErrorsNative() throws Exception {
        final Logs logs = new Logs();
        final AtomicInteger errors = new AtomicInteger();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .onError((context, error) -> errors.incrementAndGet())
                .logRecorder(logs)
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/status")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .isInstanceOf(WebClientResponseException.BadRequest.class);
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(Integer.class)
                        .block(Duration.ofSeconds(5)))
                .isInstanceOf(org.springframework.core.codec.DecodingException.class);
        logs.await(2);
        assertThat(errors).hasValue(0);
        assertThat(logs.events(HttpLogEvent.COMPLETE))
                .allSatisfy(event -> assertThat(event.fields().get("outcome")).isIn("complete", "cancelled"));
    }

    @Test
    void reportsBeforeFailuresAndSuppressesErrorObserverFailures() throws Exception {
        final IllegalStateException failure = new IllegalStateException("signing failed");
        final IllegalStateException observerFailure = new IllegalStateException("observer failed");
        final AtomicReference<Throwable> observed = new AtomicReference<>();
        final AtomicInteger exchanges = new AtomicInteger();
        final AtomicInteger after = new AtomicInteger();
        final Logs logs = new Logs();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .beforeRequest((context, request) -> {
                    throw failure;
                })
                .afterResponse((context, response) -> {
                    after.incrementAndGet();
                    return Mono.just(response);
                })
                .initialize(initialization -> initialization.builder().exchangeFunction(request -> {
                    exchanges.incrementAndGet();
                    return Mono.empty();
                }))
                .onError((context, error) -> {
                    observed.set(error);
                    throw observerFailure;
                })
                .logRecorder(logs)
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block())
                .isSameAs(failure)
                .satisfies(error -> assertThat(error.getSuppressed()).contains(observerFailure));
        logs.await(1);
        assertThat(observed.get()).isSameAs(failure);
        assertThat(exchanges).hasValue(0);
        assertThat(after).hasValue(0);
    }

    @Test
    void reportsAsyncBeforeAndTransportFailures() {
        final IllegalStateException failure = new IllegalStateException("async failed");
        final AtomicReference<Throwable> observed = new AtomicReference<>();
        final WebClient beforeClient = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .beforeRequest((context, request) -> Mono.error(failure))
                .onError((context, error) -> observed.set(error))
                .build();
        assertThatThrownBy(() -> beforeClient
                        .get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block())
                .isSameAs(failure);
        assertThat(observed.get()).isSameAs(failure);
        final WebClient transportClient = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization.builder().exchangeFunction(request -> Mono.error(failure)))
                .onError((context, error) -> observed.set(error))
                .build();
        assertThatThrownBy(() -> transportClient
                        .get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block())
                .isSameAs(failure);
        assertThat(observed.get()).isSameAs(failure);
    }

    @Test
    void releasesUnconsumedPooledBodyOnAfterFailureAndDoesNotResubscribeConsumedBody() {
        for (final boolean consume : List.of(false, true)) {
            final NettyDataBuffer buffer = WebClientFactoryTest.pooled("body");
            final AtomicInteger subscriptions = new AtomicInteger();
            final AtomicReference<Throwable> observed = new AtomicReference<>();
            final IllegalStateException failure = new IllegalStateException("after failed");
            final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                    .initialize(initialization -> initialization
                            .builder()
                            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                    .body(Flux.defer(() -> {
                                        subscriptions.incrementAndGet();
                                        return Flux.just(buffer);
                                    }))
                                    .build())))
                    .afterResponse((context, response) ->
                            consume ? response.bodyToMono(String.class).then(Mono.error(failure)) : Mono.error(failure))
                    .onError((context, error) -> observed.set(error))
                    .build();
            assertThatThrownBy(() -> client.get()
                            .uri("/json")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block())
                    .isSameAs(failure);
            assertThat(observed.get()).isSameAs(failure);
            assertThat(subscriptions).hasValue(1);
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
        }
    }

    @Test
    void reportsBodyFailureAndCompletesExactlyOnce() throws Exception {
        final Logs logs = new Logs();
        final AtomicInteger errorCalls = new AtomicInteger();
        final AtomicReference<Throwable> observed = new AtomicReference<>();
        final IllegalStateException failure = new IllegalStateException("body failed");
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body(Flux.error(failure))
                                .build())))
                .onError((context, error) -> {
                    errorCalls.incrementAndGet();
                    observed.set(error);
                })
                .logRecorder(logs)
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block())
                .isInstanceOf(WebClientResponseException.class)
                .hasCause(failure);
        logs.await(1);
        assertThat(observed.get()).isSameAs(failure);
        assertThat(errorCalls).hasValue(1);
        assertThat(logs.events(HttpLogEvent.COMPLETE)).hasSize(1);
        assertThat(logs.events(HttpLogEvent.COMPLETE).getFirst().fields().get("outcome"))
                .isEqualTo("error");
    }

    @Test
    void completesOnlyAfterTheResponseBodyFinishes() throws Exception {
        final Logs logs = new Logs();
        final Sinks.One<String> body = Sinks.one();
        final CountDownLatch subscribed = new CountDownLatch(1);
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body(body.asMono()
                                        .<DataBuffer>map(value -> DefaultDataBufferFactory.sharedInstance.wrap(
                                                value.getBytes(StandardCharsets.UTF_8)))
                                        .doOnSubscribe(ignored -> subscribed.countDown())
                                        .flux())
                                .build())))
                .logRecorder(logs)
                .build();
        final var result =
                client.get().uri("/json").retrieve().bodyToMono(String.class).toFuture();
        assertThat(subscribed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(logs.events(HttpLogEvent.RESPONSE)).hasSize(1);
        assertThat(logs.events(HttpLogEvent.COMPLETE)).isEmpty();
        assertThat(body.tryEmitValue("done")).isEqualTo(Sinks.EmitResult.OK);
        assertThat(result.get(3, TimeUnit.SECONDS)).isEqualTo("done");
        logs.await(1);
        assertThat(logs.events(HttpLogEvent.COMPLETE).getFirst().fields().get("outcome"))
                .isEqualTo("complete");
    }

    @Test
    void releasesReplacementBodyWhenALaterProcessorFails() {
        final NettyDataBuffer original = WebClientFactoryTest.pooled("original");
        final NettyDataBuffer replacement = WebClientFactoryTest.pooled("replacement");
        final AtomicInteger subscriptions = new AtomicInteger();
        final IllegalStateException failure = new IllegalStateException("later processor failed");
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body(Flux.defer(() -> {
                                    subscriptions.incrementAndGet();
                                    return Flux.just(original);
                                }))
                                .build())))
                .afterResponse((context, response) -> response.bodyToMono(String.class)
                        .map(value -> response.mutate()
                                .body(ignored -> Flux.just(replacement))
                                .build()))
                .afterResponse((context, response) -> {
                    throw failure;
                })
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isSameAs(failure);
        assertThat(original.getNativeBuffer().refCnt()).isZero();
        assertThat(replacement.getNativeBuffer().refCnt()).isZero();
        assertThat(subscriptions).hasValue(1);
    }

    @Test
    void afterFailureCancelsAnUnendingBodyImmediately() {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final IllegalStateException failure = new IllegalStateException("after failed");
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body(Flux.<DataBuffer>never().doOnCancel(() -> cancelled.set(true)))
                                .build())))
                .afterResponse((context, response) -> Mono.error(failure))
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isSameAs(failure);
        assertThat(cancelled).isTrue();
    }

    @Test
    void rejectsEmptyProcessorsAndBoundsCodecAndReadTimeout() {
        final WebClient before = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .beforeRequest((context, request) -> Mono.empty())
                .build();
        assertThatThrownBy(() -> before.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("请求回调");
        final WebClient after = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .afterResponse((context, response) -> Mono.empty())
                .build();
        assertThatThrownBy(() -> after.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("响应回调");
        final WebClient bounded = WebClientFactory.builder(new WebClientConfig(
                        "bounded",
                        this.baseUrl,
                        new HttpHeaders(),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(100),
                        8,
                        HttpLogOptions.defaults()))
                .build();
        assertThatThrownBy(() -> bounded.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isInstanceOf(WebClientResponseException.class)
                .hasCauseInstanceOf(org.springframework.core.io.buffer.DataBufferLimitException.class);
        assertThatThrownBy(() -> bounded.get()
                        .uri("/slow")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(3)))
                .isInstanceOf(org.springframework.web.reactive.function.client.WebClientRequestException.class)
                .hasCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);
    }

    @Test
    void recordsCancellationBeforeHeadersAndDuringBody() throws Exception {
        for (final boolean beforeHeaders : List.of(true, false)) {
            final Logs logs = new Logs();
            final CountDownLatch subscribed = new CountDownLatch(1);
            final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                    .initialize(initialization -> initialization
                            .builder()
                            .exchangeFunction(request -> beforeHeaders
                                    ? Mono.<ClientResponse>never().doOnSubscribe(ignored -> subscribed.countDown())
                                    : Mono.just(ClientResponse.create(HttpStatus.OK)
                                            .body(Flux.<DataBuffer>never()
                                                    .doOnSubscribe(ignored -> subscribed.countDown()))
                                            .build())))
                    .logRecorder(logs)
                    .build();
            final Disposable call = client.get()
                    .uri("/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe();
            assertThat(subscribed.await(3, TimeUnit.SECONDS)).isTrue();
            call.dispose();
            logs.await(1);
            assertThat(logs.events(HttpLogEvent.COMPLETE))
                    .singleElement()
                    .satisfies(
                            event -> assertThat(event.fields().get("outcome")).isEqualTo("cancelled"));
        }
    }

    @Test
    void cancelsAfterProcessorAndReleasesUnconsumedResponse() throws Exception {
        final NettyDataBuffer buffer = WebClientFactoryTest.pooled("body");
        final CountDownLatch processing = new CountDownLatch(1);
        final Logs logs = new Logs();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body(Flux.just(buffer))
                                .build())))
                .afterResponse((context, response) ->
                        Mono.<ClientResponse>never().doOnSubscribe(ignored -> processing.countDown()))
                .logRecorder(logs)
                .build();
        final Disposable call =
                client.get().uri("/json").retrieve().bodyToMono(String.class).subscribe();
        assertThat(processing.await(3, TimeUnit.SECONDS)).isTrue();
        call.dispose();
        logs.await(1);
        assertThat(buffer.getNativeBuffer().refCnt()).isZero();
    }

    @Test
    void initializationAndLoggingFailuresPreserveTheOriginalOutcome() {
        final IllegalStateException failure = new IllegalStateException("initialization failed");
        final IllegalStateException observer = new IllegalStateException("observer failed");
        final Logs logs = new Logs();
        assertThatThrownBy(() -> WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                        .initialize(context -> {
                            throw failure;
                        })
                        .onInitializationError((context, error) -> {
                            throw observer;
                        })
                        .logRecorder(logs)
                        .build())
                .isSameAs(failure)
                .satisfies(error -> assertThat(error.getSuppressed()).contains(observer));
        assertThat(logs.entries).extracting(Event::name).containsExactly(HttpLogEvent.INITIALIZATION_ERROR);
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .logRecorder((event, fields) -> {
                    throw new IllegalStateException("record failed");
                })
                .build();
        assertThat(client.get().uri("/json").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5)))
                .contains("hello");
    }

    @Test
    void disabledLoggingSkipsRequestHeaderInspectionAndKeepsLifecycleHooks() {
        final AtomicInteger headerReads = new AtomicInteger();
        final AtomicInteger afterCalls = new AtomicInteger();
        final AtomicReference<HttpExchangeContext> context = new AtomicReference<>();
        final List<String> values = new AbstractList<>() {
            @Override
            public String get(final int index) {
                headerReads.incrementAndGet();
                return "value";
            }

            @Override
            public int size() {
                return 1;
            }
        };
        final HttpLogOptions disabled = new HttpLogOptions(false, true, Set.of());
        final WebClient client = WebClientFactory.builder(this.config(disabled))
                .initialize(initialization -> initialization
                        .builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .body("done")
                                .build())))
                .beforeRequest((exchange, request) -> {
                    context.set(exchange);
                    final ClientRequest prepared = ClientRequest.from(request)
                            .headers(headers -> headers.put("X-Probe", values))
                            .build();
                    headerReads.set(0);
                    return Mono.just(prepared);
                })
                .afterResponse((exchange, response) -> {
                    assertThat(exchange).isSameAs(context.get());
                    afterCalls.incrementAndGet();
                    return Mono.just(response);
                })
                .logRecorder((event, fields) -> {
                    throw new AssertionError("关闭日志时不应调用接收器");
                })
                .build();
        assertThat(client.get().uri("/json").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(3)))
                .isEqualTo("done");
        assertThat(headerReads).hasValue(0);
        assertThat(afterCalls).hasValue(1);
    }

    @Test
    void disabledLoggingStillReportsTheOriginalFailure() {
        final IllegalStateException failure = new IllegalStateException("before failed");
        final AtomicReference<Throwable> observed = new AtomicReference<>();
        final WebClient client = WebClientFactory.builder(this.config(new HttpLogOptions(false, true, Set.of())))
                .beforeRequest((context, request) -> Mono.error(failure))
                .onError((context, error) -> observed.set(error))
                .logRecorder((event, fields) -> {
                    throw new AssertionError("关闭日志时不应调用接收器");
                })
                .build();
        assertThatThrownBy(() -> client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block())
                .isSameAs(failure);
        assertThat(observed.get()).isSameAs(failure);
    }

    @Test
    void supportsCustomConnectorAndDoesNotFollowRedirectsByDefault() {
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .connector(new JdkClientHttpConnector())
                .build();
        assertThat(client.get()
                        .uri("/json")
                        .retrieve()
                        .bodyToMono(Map.class)
                        .blockOptional(Duration.ofSeconds(5))
                        .orElseThrow()
                        .get("message"))
                .isEqualTo("hello");
        final WebClient defaultClient =
                WebClientFactory.builder(this.config(HttpLogOptions.defaults())).build();
        assertThat(defaultClient
                        .get()
                        .uri("/redirect")
                        .exchangeToMono(
                                response -> Mono.just(response.statusCode().value()))
                        .block(Duration.ofSeconds(5)))
                .isEqualTo(302);
    }

    @Test
    void logsOnlyRedactedSummariesWithoutChangingTheRequest() throws Exception {
        final Logs logs = new Logs();
        final HttpLogOptions logging = new HttpLogOptions(true, true, Set.of("account-number"));
        final WebClient client =
                WebClientFactory.builder(this.config(logging)).logRecorder(logs).build();
        final String body = "{\"password\":\"pw-secret\",\"nested\":[{\"access_token\":\"token-secret\"}],"
                + "\"account_number\":\"body-account\",\"value\":1}";
        assertThat(client.post()
                        .uri(
                                "/echo?token=query-secret&signature=query-signature&account_number=query-account&normal=ok")
                        .header("Authorization", "Bearer header-secret")
                        .header("Cookie", "session=cookie-secret")
                        .header("Account-Number", "header-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .isEqualTo(body);
        logs.await(1);
        assertThat(logs.events(HttpLogEvent.REQUEST).getFirst().fields().get("method"))
                .isEqualTo("POST");
        assertThat(logs.events(HttpLogEvent.RESPONSE).getFirst().fields().get("status"))
                .isEqualTo(200);
        assertThat(logs.entries)
                .allSatisfy(event -> assertThat(event.fields())
                        .doesNotContainKeys("headers", "requestBody", "responseBody", "stage"));
        assertThat(logs.toString())
                .contains("***", "normal=ok")
                .doesNotContain(
                        "pw-secret",
                        "token-secret",
                        "query-secret",
                        "header-secret",
                        "cookie-secret",
                        "query-signature",
                        "query-account",
                        "header-account",
                        "body-account");
    }

    @Test
    void preservesFormMultipartAndStreamingBodies() throws Exception {
        final Logs logs = new Logs();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .logRecorder(logs)
                .build();
        assertThat(client.post()
                        .uri("/echo")
                        .body(BodyInserters.fromFormData("password", "form-secret")
                                .with("name", "alice"))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .contains("form-secret");
        assertThat(client.post()
                        .uri("/echo")
                        .body(BodyInserters.fromMultipartData("file", "file-secret"))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .contains("file-secret");
        assertThat(client.post()
                        .uri("/echo")
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .bodyValue("data: stream-secret\n\n")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .isEqualTo("data: stream-secret\n\n");
        logs.await(3);
        assertThat(logs.toString()).doesNotContain("form-secret", "file-secret", "stream-secret");
    }

    @Test
    void preservesFileTransfer(final @TempDir Path directory) throws Exception {
        final Path file = directory.resolve("request.json");
        final String content = "{\"private\":\"file-secret\"}";
        Files.writeString(file, content);
        final Logs logs = new Logs();
        final WebClient client = WebClientFactory.builder(this.config(HttpLogOptions.defaults()))
                .logRecorder(logs)
                .build();
        assertThat(client.post()
                        .uri("/echo")
                        .body(BodyInserters.fromResource(new FileSystemResource(file)))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(5)))
                .isEqualTo(content);
        logs.await(1);
        assertThat(logs.toString()).doesNotContain("file-secret");
    }

    @Test
    void preservesNativeRequestFraming() {
        final List<String> framing = new ArrayList<>();
        for (final WebClient client : List.of(
                WebClient.builder().baseUrl(this.baseUrl).build(),
                WebClientFactory.builder(this.config(HttpLogOptions.defaults())).build())) {
            final var response = client.post()
                    .uri("/echo")
                    .bodyValue(Map.of("message", "hello"))
                    .retrieve()
                    .toEntity(String.class)
                    .blockOptional(Duration.ofSeconds(5))
                    .orElseThrow();
            framing.add(java.util.Objects.requireNonNull(response.getHeaders().getFirst("X-Body-Framing")));
            assertThat(response.getBody()).isEqualTo("{\"message\":\"hello\"}");
        }
        assertThat(framing.getFirst()).isEqualTo(framing.getLast());
    }

    @Test
    @SuppressWarnings("NullAway") // 验证公共边界拒绝 null。
    void configurationCopiesHeadersAndRejectsInvalidPublicInputs() {
        final HttpHeaders headers = new HttpHeaders();
        final List<String> values = new ArrayList<>(List.of("original"));
        headers.put("X-Test", values);
        final WebClientConfig config = new WebClientConfig(
                "test",
                this.baseUrl,
                headers,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1024,
                HttpLogOptions.defaults());
        values.add("changed");
        headers.add("X-New", "changed");
        assertThat(config.defaultHeaders().get("X-Test")).containsExactly("original");
        assertThat(config.defaultHeaders()).doesNotContainKey("X-New");
        assertThatThrownBy(() -> config.defaultHeaders().add("X-Test", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> WebClientConfig.defaults(" ")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> new WebClientConfig(
                        "test",
                        "not a url",
                        headers,
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        0,
                        HttpLogOptions.defaults()))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> WebClientFactory.builder(config).beforeRequest(null))
                .isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> WebClientFactory.builder(config).logRecorder(null))
                .isInstanceOf(ErrorCodeException.class);
    }

    private WebClientConfig config(final HttpLogOptions logging) {
        return new WebClientConfig(
                "test",
                this.baseUrl,
                new HttpHeaders(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                256 * 1024,
                logging);
    }

    private static NettyDataBuffer pooled(final String value) {
        return new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT)
                .allocateBuffer(value.getBytes(StandardCharsets.UTF_8).length)
                .write(value.getBytes(StandardCharsets.UTF_8));
    }

    record Event(HttpLogEvent name, Map<String, ?> fields) {}

    static final class Logs implements HttpLogRecorder {
        private final List<Event> entries = new CopyOnWriteArrayList<>();

        @Override
        public void record(final HttpLogEvent event, final Map<String, ?> fields) {
            this.entries.add(new Event(event, fields));
        }

        List<Event> events(final HttpLogEvent name) {
            return this.entries.stream().filter(event -> event.name() == name).toList();
        }

        void await(final int count) throws InterruptedException {
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (this.events(HttpLogEvent.COMPLETE).size() < count && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(this.events(HttpLogEvent.COMPLETE)).hasSize(count);
        }

        @Override
        public String toString() {
            return this.entries.toString();
        }
    }
}
