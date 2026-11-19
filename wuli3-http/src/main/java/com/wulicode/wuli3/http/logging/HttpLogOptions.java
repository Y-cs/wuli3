package com.wulicode.wuli3.http.logging;

import com.wulicode.wuli3.core.assertion.Asserts;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * HTTP 汇总日志的详情选择、有界采集与脱敏选项。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public record HttpLogOptions(
        boolean enabled, boolean redact, Set<String> sensitiveNames, Set<HttpLogItem> items, int maxBodyBytes) {

    private static final int DEFAULT_MAX_BODY_BYTES = 8 * 1024;

    private static final Set<String> DEFAULT_SENSITIVE_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "password",
            "passwd",
            "secret",
            "client-secret",
            "token",
            "access-token",
            "refresh-token",
            "api-key",
            "x-api-key",
            "access-key",
            "signature");

    /** 创建仅包含基础摘要的日志选项；正文采集上限默认为每侧 8 KiB。 */
    public HttpLogOptions(final boolean enabled, final boolean redact, final @Nullable Set<String> sensitiveNames) {
        this(enabled, redact, sensitiveNames, Set.of(), HttpLogOptions.DEFAULT_MAX_BODY_BYTES);
    }

    /**
     * 校验采集上限并冻结敏感名称与详情选项。
     *
     * @param sensitiveNames 补充的业务敏感名称；null 或空集合表示仅使用内置名单
     * @param items 需要采集的详情；空集合表示仅记录基础摘要
     * @param maxBodyBytes 每侧正文允许采集的最大字节数；超限时省略正文，不改变 HTTP 结果
     */
    public HttpLogOptions(
            final boolean enabled,
            final boolean redact,
            final @Nullable Set<String> sensitiveNames,
            final Set<HttpLogItem> items,
            final int maxBodyBytes) {
        Asserts.whenNull(items).throwIllegalArgumentException("日志详情选项不能为空");
        Asserts.whenTrue(items.stream().anyMatch(item -> item == null))
                .throwIllegalArgumentException("日志详情选项不能包含 null");
        Asserts.whenFalse(maxBodyBytes > 0).throwIllegalArgumentException("正文日志采集上限必须大于 0");
        final Set<String> names = new HashSet<>(HttpLogOptions.DEFAULT_SENSITIVE_NAMES);
        if (sensitiveNames != null) {
            for (final String name : sensitiveNames) {
                Asserts.whenBlank(name).throwIllegalArgumentException("敏感名称不能为空白");
                names.add(name);
            }
        }
        this.enabled = enabled;
        this.redact = redact;
        this.sensitiveNames = Set.copyOf(names);
        this.items = Set.copyOf(items);
        this.maxBodyBytes = maxBodyBytes;
    }

    /** 默认只记录一条汇总摘要，并启用脱敏。 */
    public static HttpLogOptions defaults() {
        return new HttpLogOptions(true, true, Set.of());
    }
}
