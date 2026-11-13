package com.kjs.wuli3.http.webclient;

import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.http.logging.HttpLogOptions;
import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

/**
 * 显式、不可变的客户端配置；不参与 Spring 配置绑定。
 *
 * 注意：超时仅配置默认 Reactor Netty 连接器；responseTimeout 是网络读取间隔上限。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public record WebClientConfig(
        String name,
        @Nullable String baseUrl,
        HttpHeaders defaultHeaders,
        Duration connectTimeout,
        Duration responseTimeout,
        int maxInMemorySize,
        HttpLogOptions logging) {

    /** 校验公共配置并深拷贝默认请求头。 */
    public WebClientConfig {
        Asserts.whenBlank(name).throwIllegalArgumentException("客户端名称不能为空");
        Asserts.whenNull(defaultHeaders).throwIllegalArgumentException("默认请求头不能为空");
        Asserts.whenNull(connectTimeout).throwIllegalArgumentException("连接超时不能为空");
        Asserts.whenNull(responseTimeout).throwIllegalArgumentException("读取间隔超时不能为空");
        Asserts.whenNull(logging).throwIllegalArgumentException("日志配置不能为空");
        Asserts.whenFalse(connectTimeout.compareTo(Duration.ofMillis(1)) >= 0
                        && connectTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0)
                .throwIllegalArgumentException("连接超时必须在 1 毫秒至 Integer.MAX_VALUE 毫秒之间");
        Asserts.whenFalse(responseTimeout.compareTo(Duration.ofMillis(1)) >= 0)
                .throwIllegalArgumentException("读取间隔超时不能小于 1 毫秒");
        Asserts.whenFalse(maxInMemorySize > 0).throwIllegalArgumentException("编解码缓存上限必须大于 0");
        if (baseUrl != null) {
            WebClientConfig.validateBaseUrl(baseUrl);
        }
        final HttpHeaders headers = new HttpHeaders();
        defaultHeaders.forEach((key, values) -> {
            Asserts.whenBlank(key).throwIllegalArgumentException("请求头名称不能为空");
            Asserts.whenNull(values).throwIllegalArgumentException("请求头值不能为空");
            Asserts.whenTrue(values.stream().anyMatch(value -> value == null))
                    .throwIllegalArgumentException("请求头值不能为空");
            headers.put(key, java.util.List.copyOf(values));
        });
        defaultHeaders = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    /** 创建连接超时 3 秒、读取间隔 30 秒、编解码缓存 256 KiB 的默认配置。 */
    public static WebClientConfig defaults(final String name) {
        return new WebClientConfig(
                name,
                null,
                new HttpHeaders(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                256 * 1024,
                HttpLogOptions.defaults());
    }

    private static void validateBaseUrl(final String value) {
        try {
            final URI uri = URI.create(value);
            Asserts.whenFalse(("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                            && uri.getHost() != null
                            && uri.getUserInfo() == null
                            && uri.getFragment() == null)
                    .throwIllegalArgumentException("baseUrl 必须是无凭据、无片段的 HTTP(S) 地址");
        } catch (final IllegalArgumentException exception) {
            Asserts.whenTrue(true).throwIllegalArgumentException("baseUrl 格式无效");
        }
    }
}
