package com.kjs.wuli3.http.internal.logging;

import com.kjs.wuli3.http.logging.HttpLogEvent;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import com.kjs.wuli3.http.logging.HttpLogRecorder;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 隔离共享日志接收器故障并生成脱敏地址。
 *
 * 注意：仅供模块内部跨包调用，不属于对外扩展契约。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public final class HttpLogs {

    // 保留原有日志分类，避免改变应用已配置的日志级别。
    private static final Logger LOGGER = LoggerFactory.getLogger("com.kjs.wuli3.http.WebClientFactory");
    private static final HttpLogRecorder NOOP = (event, fields) -> {};

    private HttpLogs() {}

    /** 创建使用 SLF4J 结构化字段的默认接收器。 */
    public static HttpLogRecorder defaultRecorder() {
        return (event, fields) -> {
            var builder = HttpLogs.LOGGER
                    .atInfo()
                    .addKeyValue("http.event", event.name().toLowerCase(Locale.ROOT));
            for (final var field : fields.entrySet()) {
                builder = builder.addKeyValue(field.getKey(), field.getValue());
            }
            builder.log("HTTP client");
        };
    }

    /** 按日志开关包装共享接收器，运行时异常不改变 HTTP 信号。 */
    public static HttpLogRecorder safeRecorder(final HttpLogOptions options, final HttpLogRecorder recorder) {
        return options.enabled() ? (event, fields) -> HttpLogs.record(recorder, event, fields) : HttpLogs.NOOP;
    }

    private static void record(final HttpLogRecorder recorder, final HttpLogEvent event, final Map<String, ?> fields) {
        try {
            recorder.record(event, fields);
        } catch (final RuntimeException exception) {
            // 日志故障不能改变被观测的 HTTP 信号。
        }
    }

    /** 生成日志地址，始终省略凭据、片段，并按配置脱敏查询参数。 */
    public static String url(final URI uri, final HttpLogOptions options) {
        final String query = uri.getRawQuery();
        final String authority = uri.getRawAuthority();
        final String safeAuthority = authority == null ? "" : authority.substring(authority.lastIndexOf('@') + 1);
        final String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        final String base = (uri.getScheme() == null ? "" : uri.getScheme() + "://") + safeAuthority + path;
        if (query == null) {
            return base;
        }
        try {
            return base + "?" + (options.redact() ? HttpLogs.query(query, options) : query);
        } catch (final RuntimeException exception) {
            return base + "?<omitted>";
        }
    }

    private static String query(final String text, final HttpLogOptions options) {
        final String[] pairs = text.split("&", -1);
        for (int index = 0; index < pairs.length; index++) {
            final int equals = pairs[index].indexOf('=');
            final String key = equals < 0 ? pairs[index] : pairs[index].substring(0, equals);
            // 同时校验值的编码；异常时省略全部查询参数。
            URLDecoder.decode(pairs[index], StandardCharsets.UTF_8);
            if (HttpLogs.sensitive(URLDecoder.decode(key, StandardCharsets.UTF_8), options)) {
                pairs[index] = key + "=***";
            }
        }
        return String.join("&", pairs);
    }

    private static boolean sensitive(final String name, final HttpLogOptions options) {
        final String normalized = HttpLogs.normalize(name);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || options.sensitiveNames().stream()
                        .anyMatch(value -> HttpLogs.normalize(value).equals(normalized));
    }

    private static String normalize(final String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** 将观察器异常附加到原始异常，避免对同一实例执行自抑制。 */
    @SuppressWarnings("ReferenceEquality") // Throwable 禁止自抑制，需要比较实例身份。
    public static void suppress(final Throwable primary, final Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }
}
