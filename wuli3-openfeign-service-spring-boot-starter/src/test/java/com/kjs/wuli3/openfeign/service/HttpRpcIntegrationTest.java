package com.kjs.wuli3.openfeign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.dubbo.autoconfigure.DubboProperties;
import com.kjs.wuli3.dubbo.context.DubboContextConsumerFilter;
import com.kjs.wuli3.dubbo.context.DubboContextProviderFilter;
import com.kjs.wuli3.dubbo.error.DubboErrorConsumerFilter;
import com.kjs.wuli3.dubbo.error.DubboErrorProviderFilter;
import com.kjs.wuli3.json.datatype.desensitization.DesensitizationTypes;
import com.kjs.wuli3.json.datatype.desensitization.Desensitized;
import com.kjs.wuli3.openfeign.client.Wuli3FeignConfiguration;
import com.kjs.wuli3.openfeign.client.error.HttpRpcErrorDecoder;
import com.kjs.wuli3.openfeign.service.protocol.HttpRpcProtocol;
import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.spring.logging.LogMdcKeys;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用两个真实 HTTP 应用验证公开 Web 与内部调用隔离，以及 Dubbo Filter 混合调用的语义。
 *
 * 注意：HTTP 使用真实网络；Dubbo 跳通过真实 Filter 与本地测试 Invoker 连接，不代表注册中心或 Dubbo 网络验证。
 *
 * @author 国杨 create on 2026/11/5 10:00
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpRpcIntegrationTest {

    private final ConfigurableApplicationContext provider;
    private final ConfigurableApplicationContext consumer;
    private final InternalClient client;
    private final ContextManager manager;
    private final String providerUrl;
    private final String consumerUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    HttpRpcIntegrationTest() {
        this.provider = new SpringApplicationBuilder(Provider.class)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "dubbo.enabled=false",
                        "wuli3.http-rpc.enabled=true",
                        "application.service.service-code=provider")
                .run();
        this.providerUrl = "http://127.0.0.1:"
                + ((ServletWebServerApplicationContext) this.provider)
                        .getWebServer()
                        .getPort();
        this.consumer = new SpringApplicationBuilder(Consumer.class)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "dubbo.enabled=false",
                        "sample.rpc-url=" + this.providerUrl,
                        "application.service.service-code=consumer")
                .run();
        this.consumerUrl = "http://127.0.0.1:"
                + ((ServletWebServerApplicationContext) this.consumer)
                        .getWebServer()
                        .getPort();
        this.client = this.consumer.getBean(InternalClient.class);
        this.manager = this.consumer.getBean(ContextManager.class);
    }

    /** 关闭真实容器，应用实例的生命周期仅归应用根容器所有。 */
    @AfterAll
    void closeApplications() {
        this.consumer.close();
        this.provider.close();
    }

    /** 同一 DTO 在公开出口包装并脱敏，内部出口保留原始传输值。 */
    @Test
    void separatesPublicPresentationFromInternalJson() throws Exception {
        final HttpResponse<String> publicResponse = this.get(this.providerUrl + "/public/value", false);
        final Sample internal = this.client.value();
        assertThat(publicResponse.statusCode()).isEqualTo(200);
        assertThat(publicResponse.body()).contains("\"data\"", "138****8000").doesNotContain("13800138000");
        assertThat(internal.phone()).isEqualTo("13800138000");
        final HttpResponse<String> accidental = this.get(this.providerUrl + "/value", true);
        assertThat(accidental.statusCode()).isEqualTo(404);
        assertThat(accidental.body()).doesNotContain("13800138000");
        final Map<String, Object> plain =
                this.consumer.getBean(PlainClient.class).value();
        assertThat(plain).containsKey("data").doesNotContainKey("phone");
    }

    /** Feign 请求还原完整上下文，后续无上下文请求不会复用线程残留。 */
    @Test
    void propagatesContextAndRestoresCallerAndServerThreads() throws Exception {
        final ContextState state = ContextState.of(
                new InvocationContext("198.51.100.8", "mixed-request"),
                new AuthContext(PrincipalType.CUSTOMER, "用户-42", "permissions-v2"));
        MDC.put(LogMdcKeys.REQUEST_ID, "previous-mdc");
        try {
            final Sample sample = this.manager.with(state).call(this.client::value);
            assertThat(sample.requestId()).isEqualTo("mixed-request");
            assertThat(sample.originIp()).isEqualTo("198.51.100.8");
            assertThat(sample.principal()).isEqualTo("用户-42");
            assertThat(sample.mdc()).isEqualTo("mixed-request");
            assertThat(this.manager.capture().values()).isEmpty();
            assertThat(MDC.get(LogMdcKeys.REQUEST_ID)).isEqualTo("previous-mdc");
            for (int index = 0; index < 12; index++) {
                final Sample empty = this.client.value();
                assertThat(empty.requestId()).isEqualTo("none");
                assertThat(empty.principal()).isEqualTo("none");
                assertThat(empty.mdc()).isEqualTo("none");
            }
        } finally {
            MDC.remove(LogMdcKeys.REQUEST_ID);
        }
    }

    /** 四种可见性在内部均保留完整错误，最终公开 Web 才掩码。 */
    @ParameterizedTest
    @EnumSource(ErrorVisibility.class)
    void preservesErrorsUntilPublicBoundary(final ErrorVisibility visibility) throws Exception {
        assertThatThrownBy(() -> this.client.failure(visibility.name()))
                .isInstanceOfSatisfying(ErrorCodeException.class, error -> {
                    assertThat(error.getMessage()).isEqualTo("内部业务详情");
                    assertThat(error.getVisibility()).isEqualTo(visibility);
                    assertThat(error.asRemoteError()).isPresent();
                });
        final HttpResponse<String> response = this.get(this.consumerUrl + "/public/failure/" + visibility, false);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().contains("内部业务详情"))
                .isEqualTo(visibility == ErrorVisibility.PUBLIC || visibility == ErrorVisibility.MESSAGE_ONLY);
        assertThat(response.body()).doesNotContain("originalCode", "sourceService", "visibility");
    }

    /** 无返回值接口失败仍抛出远程业务异常；中文请求正文按内部 JSON 编解码。 */
    @Test
    void handlesVoidFailuresAndRequestBodies() {
        assertThatThrownBy(this.client::voidFailure)
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("内部业务详情");
        final Sample sent = new Sample("13800138000", "中文请求", "ip", "id", "mdc");
        assertThat(this.client.echo(sent)).isEqualTo(sent);
    }

    /** 身份拒绝发生在认证上下文解码前，不能被非法传播头变为公开错误。 */
    @Test
    void authenticatesBeforeDecodingUserContext() throws Exception {
        final int before = this.provider.getBean(InternalEndpoint.class).calls.get();
        final HttpResponse<String> response = this.http.send(
                HttpRequest.newBuilder(URI.create(this.providerUrl + "/internal/rpc/value"))
                        .header(AuthContext.PRINCIPAL_TYPE, "invalid")
                        .header(AuthContext.PRINCIPAL_ID, "forged")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("\"data\"", "forged", "WEB.");
        assertThat(response.headers().firstValue(HttpRpcProtocol.VERSION_HEADER))
                .contains(HttpRpcProtocol.VERSION);
        assertThat(this.provider.getBean(InternalEndpoint.class).calls.get()).isEqualTo(before);
    }

    /** 版本缺失或不匹配保持内部参数错误，不进入公开错误出口。 */
    @Test
    void rejectsMissingAndUnsupportedProtocolVersions() throws Exception {
        for (final String version : java.util.List.of("", "2")) {
            final HttpRequest.Builder request = HttpRequest.newBuilder(
                            URI.create(this.providerUrl + "/internal/rpc/value"))
                    .header("X-Test-Service-Identity", "test-only");
            if (!version.isEmpty()) {
                request.header(HttpRpcProtocol.VERSION_HEADER, version);
            }
            final HttpResponse<String> response =
                    this.http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            this.assertInternalFailure(response, 400);
            assertThat(response.body()).contains("PROVIDER.HTTP_RPC.INVALID_REQUEST");
        }
    }

    /** 内部路由、方法、正文与容器错误均不经过公开 ApiResponse 或 /error。 */
    @Test
    void containsProtocolAndContainerFailures() throws Exception {
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/missing", true), 404);
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/echo", true), 405);
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/unknown", true), 500);
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/send-error", true), 503);
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/oversized-error", true), 500);
        this.assertInternalFailure(this.get(this.providerUrl + "/internal/rpc/count/invalid", true), 400);
        final HttpResponse<String> badJson = this.http.send(
                this.internalRequest("/echo")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        this.assertInternalFailure(badJson, 400);
        final int before = this.provider.getBean(InternalEndpoint.class).calls.get();
        final HttpResponse<String> invalidDto = this.http.send(
                this.internalRequest("/echo")
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"phone\":\"\",\"requestId\":\"id\",\"originIp\":\"ip\",\"principal\":\"id\",\"mdc\":\"mdc\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        this.assertInternalFailure(invalidDto, 400);
        assertThat(this.provider.getBean(InternalEndpoint.class).calls.get()).isEqualTo(before);
        final HttpResponse<String> duplicate = this.http.send(
                this.internalRequest("/value")
                        .header(InvocationContext.REQUEST_ID, "first")
                        .header(InvocationContext.REQUEST_ID, "second")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        this.assertInternalFailure(duplicate, 400);
    }

    /** HTTP 与真实 Dubbo Filter 可以交错使用同一上下文和错误协议。 */
    @Test
    void supportsMixedDubboHopsInBothDirections() throws Exception {
        final ContextState state = ContextState.of(new InvocationContext("198.51.100.9", "cross-protocol"));
        final Sample httpThenDubbo = this.manager.with(state).call(this.client::mixed);
        assertThat(httpThenDubbo.requestId()).isEqualTo("cross-protocol");
        final Sample dubboThenHttp = this.manager
                .with(state)
                .call(() -> HttpRpcIntegrationTest.dubboHop(
                        this.manager, this.consumer.getBean(ContextPropagator.class), this.client::value));
        assertThat(dubboThenHttp.requestId()).isEqualTo("cross-protocol");
        assertThatThrownBy(this.client::mixedFailure).isInstanceOfSatisfying(ErrorCodeException.class, error -> {
            assertThat(error.getVisibility()).isEqualTo(ErrorVisibility.INTERNAL);
            assertThat(error.getMessage()).isEqualTo("内部业务详情");
            assertThat(error.asRemoteError().orElseThrow().sourceService()).isEqualTo("dubbo-source");
        });
    }

    private HttpRequest.Builder internalRequest(final String path) {
        return HttpRequest.newBuilder(URI.create(this.providerUrl + "/internal/rpc" + path))
                .header("X-Test-Service-Identity", "test-only")
                .header(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
    }

    private HttpResponse<String> get(final String url, final boolean authenticated) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (authenticated) {
            request.header("X-Test-Service-Identity", "test-only")
                    .header(HttpRpcProtocol.VERSION_HEADER, HttpRpcProtocol.VERSION);
        }
        return this.http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertInternalFailure(final HttpResponse<String> response, final int status) throws IOException {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).contains(HttpRpcProtocol.ERROR_CONTENT_TYPE);
        assertThat(response.body()).doesNotContain("\"data\"", "WEB.", "secret-stack");
        final Map<String, java.util.Collection<String>> headers =
                new HashMap<>(response.headers().map());
        try (final Response transport = Response.builder()
                .request(Request.create(
                        Request.HttpMethod.valueOf(response.request().method()),
                        response.uri().toString(),
                        Map.of(),
                        new byte[0],
                        StandardCharsets.UTF_8,
                        new RequestTemplate()))
                .status(response.statusCode())
                .headers(headers)
                .body(response.body().getBytes(StandardCharsets.UTF_8))
                .build()) {
            assertThat(new HttpRpcErrorDecoder().decode("sample", transport)).isInstanceOf(ErrorCodeException.class);
        }
    }

    /** 使用真实协议 Filter 连接本地 Invoker，替代本测试范围之外的 Dubbo 网络与注册中心。 */
    private static Sample dubboHop(
            final ContextManager manager,
            final ContextPropagator propagator,
            final java.util.function.Supplier<Sample> operation) {
        final Map<String, String> attachments = new HashMap<>();
        final Invocation invocation = mock(Invocation.class);
        doAnswer(call -> {
                    attachments.put(call.getArgument(0), call.getArgument(1));
                    return null;
                })
                .when(invocation)
                .setAttachment(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(invocation.getAttachment(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> attachments.get(call.getArgument(0)));
        final Invoker<?> target = mock(Invoker.class);
        final URL url = URL.valueOf("dubbo://127.0.0.1:20880/service?application=dubbo-source");
        when(target.getUrl()).thenReturn(url);
        when(target.invoke(invocation)).thenAnswer(ignored -> {
            try {
                return new AppResponse(operation.get());
            } catch (final ErrorCodeException error) {
                return new AppResponse(error);
            }
        });
        final DubboProperties properties = new DubboProperties();
        final DubboContextProviderFilter contextProvider = new DubboContextProviderFilter();
        contextProvider.setContextManager(manager);
        contextProvider.setContextPropagator(propagator);
        contextProvider.setDubboProperties(properties);
        final DubboErrorProviderFilter errorProvider = new DubboErrorProviderFilter();
        errorProvider.setDubboProperties(properties);
        final Invoker<?> provider = mock(Invoker.class);
        when(provider.getUrl()).thenReturn(url);
        when(provider.invoke(invocation)).thenAnswer(ignored -> errorProvider.invoke(target, invocation));
        final Invoker<?> transport = mock(Invoker.class);
        when(transport.getUrl()).thenReturn(url);
        when(transport.invoke(invocation)).thenAnswer(ignored -> contextProvider.invoke(provider, invocation));
        final DubboErrorConsumerFilter errorConsumer = new DubboErrorConsumerFilter();
        final Invoker<?> consumer = mock(Invoker.class);
        when(consumer.invoke(invocation)).thenAnswer(ignored -> errorConsumer.invoke(transport, invocation));
        final DubboContextConsumerFilter contextConsumer = new DubboContextConsumerFilter();
        final com.kjs.wuli3.propagation.store.ContextReader reader =
                mock(com.kjs.wuli3.propagation.store.ContextReader.class);
        when(reader.state()).thenAnswer(ignored -> manager.state());
        contextConsumer.setContextReader(reader);
        contextConsumer.setContextPropagator(propagator);
        contextConsumer.setDubboProperties(properties);
        final Result result = contextConsumer.invoke(consumer, invocation);
        if (result.hasException()) {
            if (result.getException() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(result.getException());
        }
        return (Sample) result.getValue();
    }

    /** 共享业务传输值，脱敏注解只应在公开展示配置中生效。 */
    record Sample(
            @jakarta.validation.constraints.NotBlank @Desensitized(type = DesensitizationTypes.PHONE)
            String phone,

            String requestId,
            String originIp,
            String principal,
            String mdc) {}

    @FeignClient(
            name = "internal-sample",
            url = "${sample.rpc-url}/internal/rpc",
            configuration = {Wuli3FeignConfiguration.class, ServiceIdentity.class})
    interface InternalClient {
        @GetMapping("/value")
        Sample value();

        @GetMapping("/failure/{visibility}")
        Sample failure(@PathVariable("visibility") String visibility);

        @GetMapping("/void-failure")
        void voidFailure();

        @PostMapping("/echo")
        Sample echo(@RequestBody Sample body);

        @GetMapping("/mixed")
        Sample mixed();

        @GetMapping("/mixed-failure")
        Sample mixedFailure();
    }

    @FeignClient(name = "plain-sample", url = "${sample.rpc-url}")
    interface PlainClient {
        @GetMapping("/public/value")
        Map<String, Object> value();
    }

    static class ServiceIdentity {
        @Bean
        RequestInterceptor testServiceIdentity() {
            return request -> request.header("X-Test-Service-Identity", "test-only");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Provider {
        @Bean
        HttpRpcAuthenticator authenticator() {
            return request -> "test-only".equals(request.getHeader("X-Test-Service-Identity"));
        }

        @Bean
        InternalEndpoint endpoint(
                final InvocationContextAccessor invocation,
                final AuthContextAccessor auth,
                final ContextManager manager,
                final ContextPropagator propagator) {
            return new InternalEndpoint(invocation, auth, manager, propagator);
        }

        @Bean
        PublicEndpoint publicEndpoint(final InternalEndpoint endpoint) {
            return new PublicEndpoint(endpoint);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableFeignClients(clients = {InternalClient.class, PlainClient.class})
    static class Consumer {
        @Bean
        ConsumerEndpoint consumerEndpoint(final InternalClient client) {
            return new ConsumerEndpoint(client);
        }
    }

    @HttpRpcService
    static class InternalEndpoint {
        private final InvocationContextAccessor invocation;
        private final AuthContextAccessor auth;
        private final ContextManager manager;
        private final ContextPropagator propagator;
        private final AtomicInteger calls = new AtomicInteger();

        InternalEndpoint(
                final InvocationContextAccessor invocation,
                final AuthContextAccessor auth,
                final ContextManager manager,
                final ContextPropagator propagator) {
            this.invocation = invocation;
            this.auth = auth;
            this.manager = manager;
            this.propagator = propagator;
        }

        @GetMapping("/value")
        Sample value() {
            this.calls.incrementAndGet();
            return new Sample(
                    "13800138000",
                    this.invocation.requestId().orElse("none"),
                    this.invocation.originIp().orElse("none"),
                    this.auth.current().map(AuthContext::principalId).orElse("none"),
                    java.util.Objects.requireNonNullElse(MDC.get(LogMdcKeys.REQUEST_ID), "none"));
        }

        @GetMapping("/failure/{visibility}")
        Sample failure(@PathVariable("visibility") final String visibility) {
            throw new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT, "内部业务详情")
                    .withVisibility(ErrorVisibility.valueOf(visibility));
        }

        @GetMapping("/void-failure")
        void voidFailure() {
            this.failure("INTERNAL");
        }

        @PostMapping("/echo")
        Sample echo(@jakarta.validation.Valid @RequestBody final Sample body) {
            this.calls.incrementAndGet();
            return body;
        }

        @GetMapping("/unknown")
        Sample unknown() {
            throw new IllegalStateException("secret-stack");
        }

        @GetMapping("/send-error")
        void sendError(final HttpServletResponse response) throws IOException {
            response.sendError(503, "secret-stack");
        }

        /** 超限诊断必须回退为有界内部错误，不能进入公开容器出口。 */
        @GetMapping("/oversized-error")
        void oversizedError() {
            throw new ErrorCodeException(
                    CommonErrors.ILLEGAL_ARGUMENT, "x".repeat(HttpRpcProtocol.MAX_ERROR_BYTES + 1));
        }

        /** 路径类型转换失败由内部协议归类为调用方错误。 */
        @GetMapping("/count/{count}")
        int count(@PathVariable("count") final int count) {
            return count;
        }

        @GetMapping("/mixed")
        Sample mixed() {
            return HttpRpcIntegrationTest.dubboHop(this.manager, this.propagator, this::value);
        }

        @GetMapping("/mixed-failure")
        Sample mixedFailure() {
            return HttpRpcIntegrationTest.dubboHop(this.manager, this.propagator, () -> this.failure("INTERNAL"));
        }
    }

    @RestController
    static class PublicEndpoint {
        private final InternalEndpoint endpoint;

        PublicEndpoint(final InternalEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @GetMapping("/public/value")
        Sample value() {
            return this.endpoint.value();
        }
    }

    @RestController
    static class ConsumerEndpoint {
        private final InternalClient client;

        ConsumerEndpoint(final InternalClient client) {
            this.client = client;
        }

        @GetMapping("/public/failure/{visibility}")
        Sample failure(@PathVariable("visibility") final String visibility) {
            return this.client.failure(visibility);
        }
    }
}
