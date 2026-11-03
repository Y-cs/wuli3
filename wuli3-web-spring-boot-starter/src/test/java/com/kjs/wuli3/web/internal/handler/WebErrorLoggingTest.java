package com.kjs.wuli3.web.internal.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.core.error.builtin.CommonErrors;
import com.kjs.wuli3.core.error.model.ErrorOrigin;
import com.kjs.wuli3.core.error.model.ErrorSeverity;
import com.kjs.wuli3.core.error.model.ErrorVisibility;
import com.kjs.wuli3.core.error.propagation.ErrorCodeCarrier;
import com.kjs.wuli3.core.error.resolver.ErrorResolver;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.web.internal.advice.ApiResponseFactory;
import com.kjs.wuli3.web.response.WebResponseProperties;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 验证 Web 错误日志的独立字段、原始堆栈以及文本和 JSON 输出边界。
 *
 * @author 国杨 create on 2026/11/3 13:56
 */
class WebErrorLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(WebExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private @Nullable Level previousLevel;
    private @Nullable Map<String, String> previousMdc;

    /** 仅捕获目标类的日志，并保留调用前的日志级别和 MDC。 */
    @BeforeEach
    void captureLogs() {
        this.previousLevel = this.logger.getLevel();
        this.previousMdc = MDC.getCopyOfContextMap();
        MDC.put("requestId", "rid-logging");
        MDC.put("trace_id", "trace-logging");
        this.appender.setContext(this.logger.getLoggerContext());
        this.appender.start();
        this.logger.setLevel(Level.ERROR);
        this.logger.addAppender(this.appender);
    }

    /** 移除测试 appender，避免污染后续测试的日志和调用上下文。 */
    @AfterEach
    void restoreLogging() {
        this.logger.detachAppender(this.appender);
        this.appender.stop();
        this.logger.setLevel(this.previousLevel);
        MDC.clear();
        if (this.previousMdc != null) {
            MDC.setContextMap(this.previousMdc);
        }
    }

    /** 5xx 即使没有通知器也记录一次，内部原始码与公开响应码分别保存。 */
    @Test
    void recordsStructuredFailureWithOriginalCause() {
        final CompletionException original = WebErrorLoggingTest.failure(ErrorOrigin.SERVER, ErrorSeverity.WARNING);
        final var response = WebErrorLoggingTest.handler().handleException(original, WebErrorLoggingTest.request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(Objects.requireNonNull(response.getBody()).toString())
                .doesNotContain("REMOTE.ORIGINAL.FAILURE", "internal diagnostic");
        assertThat(this.appender.list).hasSize(1);
        final ILoggingEvent event = this.appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).isEqualTo("Web request failed");
        assertThat(WebErrorLoggingTest.fields(event))
                .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                        Map.entry("eventName", "web.request.failed"),
                        Map.entry("httpStatus", 500),
                        Map.entry("errorCode", "REMOTE.ORIGINAL.FAILURE"),
                        Map.entry("responseCode", "SYSTEM.INTERNAL_ERROR"),
                        Map.entry("errorOrigin", "SERVER"),
                        Map.entry("errorSeverity", "WARNING"),
                        Map.entry("errorVisibility", "INTERNAL"),
                        Map.entry("sourceService", "inventory-service"),
                        Map.entry("httpMethod", "POST"),
                        Map.entry("httpPath", "/orders/42"),
                        Map.entry("httpRequestId", "rid-logging"),
                        Map.entry("exceptionType", CompletionException.class.getName())));
        assertThat(event.getThrowableProxy())
                .isInstanceOfSatisfying(
                        ThrowableProxy.class,
                        proxy -> assertThat(proxy.getThrowable()).isSameAs(original));
    }

    /** 普通 4xx 保持安静，避免输入错误产生服务端错误堆栈噪音。 */
    @Test
    void doesNotLogOrdinaryClientErrors() {
        final var response = WebErrorLoggingTest.handler()
                .handleException(new ErrorCodeException(CommonErrors.ILLEGAL_ARGUMENT), WebErrorLoggingTest.request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(this.appender.list).isEmpty();
    }

    /** 严重度达到既有阈值的调用方错误仍有诊断记录，不受最终 4xx 限制。 */
    @ParameterizedTest
    @EnumSource(
            value = ErrorSeverity.class,
            names = {"CRITICAL", "FATAL"})
    void recordsCriticalClientErrors(final ErrorSeverity severity) {
        final var response = WebErrorLoggingTest.handler()
                .handleException(
                        WebErrorLoggingTest.failure(ErrorOrigin.CALLER, severity), WebErrorLoggingTest.request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(this.appender.list).hasSize(1);
        assertThat(WebErrorLoggingTest.fields(this.appender.list.getFirst()))
                .containsEntry("httpStatus", 400)
                .containsEntry("errorSeverity", severity.name())
                .containsEntry("responseCode", "COMMON.REQUEST_REJECTED");
    }

    /** 真实 Boot 编码器能输出数值状态与独立字段，且与既有 MDC 字段共存。 */
    @ParameterizedTest
    @ValueSource(strings = {"logstash", "ecs", "gelf"})
    void serializesFieldsWithExistingStructuredFormats(final String format) {
        final Map<String, String> before = MDC.getCopyOfContextMap();
        WebErrorLoggingTest.handler()
                .handleException(
                        WebErrorLoggingTest.failure(ErrorOrigin.SERVER, ErrorSeverity.CRITICAL),
                        WebErrorLoggingTest.request());
        assertThat(this.appender.list).hasSize(1);
        final String json = WebErrorLoggingTest.json(this.appender.list.getFirst(), format);
        final Map<String, Object> fields = JsonParserFactory.getJsonParser().parseMap(json);
        final String prefix = "gelf".equals(format) ? "_" : "";
        assertThat(fields)
                .containsEntry(prefix + "eventName", "web.request.failed")
                .containsEntry(prefix + "httpRequestId", "rid-logging")
                .containsEntry(prefix + "requestId", "rid-logging")
                .containsEntry(prefix + "trace_id", "trace-logging");
        assertThat(fields.get(prefix + "httpStatus"))
                .isInstanceOfSatisfying(
                        Number.class, number -> assertThat(number.intValue()).isEqualTo(500));
        assertThat(json)
                .contains("internal diagnostic", CompletionException.class.getName())
                .doesNotContain("ErrorCodeCarrier[", "query-secret", "header-secret", "body-secret");
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(before);
    }

    /** 文本布局显示键值字段并保留异常堆栈，不依赖手工拼接错误载体。 */
    @Test
    void rendersKeyValuesInTextLogs() {
        WebErrorLoggingTest.handler()
                .handleException(
                        WebErrorLoggingTest.failure(ErrorOrigin.SERVER, ErrorSeverity.CRITICAL),
                        WebErrorLoggingTest.request());
        assertThat(this.appender.list).hasSize(1);
        final PatternLayout layout = new PatternLayout();
        layout.setContext(this.logger.getLoggerContext());
        layout.setPattern("%msg %kvp%n%ex");
        layout.start();
        try {
            assertThat(layout.doLayout(this.appender.list.getFirst()))
                    .contains(
                            "Web request failed",
                            "eventName=\"web.request.failed\"",
                            "httpStatus=\"500\"",
                            "errorCode=\"REMOTE.ORIGINAL.FAILURE\"",
                            "httpRequestId=\"rid-logging\"",
                            "internal diagnostic");
        } finally {
            layout.stop();
        }
    }

    /** 缺少请求关联标识时仍可编码，并保持外层 MDC 的既有值。 */
    @Test
    void missingRequestIdDoesNotPreventLoggingOrOverwriteMdc() {
        WebErrorLoggingTest.handler(null)
                .handleException(new IllegalStateException("internal diagnostic"), WebErrorLoggingTest.request());
        assertThat(this.appender.list).hasSize(1);
        final Map<String, Object> fields = JsonParserFactory.getJsonParser()
                .parseMap(WebErrorLoggingTest.json(this.appender.list.getFirst(), "ecs"));
        assertThat(fields).containsEntry("httpRequestId", null).containsEntry("requestId", "rid-logging");
        assertThat(MDC.get("requestId")).isEqualTo("rid-logging");
    }

    /** 创建使用真实 core 投影且没有外部通知器的异常处理器。 */
    private static WebExceptionHandler handler() {
        return WebErrorLoggingTest.handler("rid-logging");
    }

    /** 指定调用上下文中的请求标识，与线程已有 MDC 独立验证。 */
    private static WebExceptionHandler handler(final @Nullable String requestId) {
        final InvocationContextAccessor accessor = mock(InvocationContextAccessor.class);
        when(accessor.requestId()).thenReturn(Optional.ofNullable(requestId));
        return new WebExceptionHandler(
                new ApiResponseFactory(accessor, new ErrorResolver("")),
                accessor,
                new WebResponseProperties(),
                List.of(),
                new DefaultWebErrorStatusResolver(),
                List.of());
    }

    /** 创建带完整远程元数据的透明包装，检验诊断身份不会被公开码覆盖。 */
    private static CompletionException failure(final ErrorOrigin origin, final ErrorSeverity severity) {
        return new CompletionException(new ErrorCodeException(new ErrorCodeCarrier(
                "REMOTE.ORIGINAL.FAILURE",
                "REMOTE.PUBLIC.FAILURE",
                "internal diagnostic",
                origin,
                severity,
                "inventory-service",
                ErrorVisibility.INTERNAL)));
    }

    /** 请求包含敏感示例值，验证日志字段不会自动扩展到查询串、请求头或正文。 */
    private static MockHttpServletRequest request() {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/42");
        request.setQueryString("token=query-secret");
        request.addHeader("Authorization", "Bearer header-secret");
        request.setContent("body-secret".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** 提取原生日志事件字段，直接验证字段类型而非从 message 反向解析。 */
    private static Map<String, Object> fields(final ILoggingEvent event) {
        assertThat(event.getKeyValuePairs()).isNotNull();
        final Map<String, Object> fields = new LinkedHashMap<>();
        for (final var pair : Objects.requireNonNull(event.getKeyValuePairs())) {
            assertThat(fields).doesNotContainKey(pair.key);
            fields.put(pair.key, pair.value);
        }
        return fields;
    }

    /** 使用独立上下文中的 Boot 编码器，避免重配测试进程的全局日志系统。 */
    private static String json(final ILoggingEvent event, final String format) {
        final LoggerContext context = new LoggerContext();
        final StructuredLogEncoder encoder = new StructuredLogEncoder();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(context);
        encoder.setFormat(format);
        try {
            encoder.start();
            return new String(encoder.encode(event), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
            context.stop();
        }
    }
}
