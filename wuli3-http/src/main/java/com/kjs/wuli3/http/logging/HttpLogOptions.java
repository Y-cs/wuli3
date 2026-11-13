package com.kjs.wuli3.http.logging;

import com.kjs.wuli3.core.assertion.Asserts;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * HTTP 摘要日志与查询参数脱敏选项。
 *
 * @author 国杨 create on 2026/11/13 15:40
 */
public record HttpLogOptions(boolean enabled, boolean redact, Set<String> sensitiveNames) {

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

    /**
     * 合并并冻结内置敏感名称与业务补充项。
     *
     * @param sensitiveNames 补充的业务敏感名称；null 或空集合表示仅使用内置名单
     */
    public HttpLogOptions(final boolean enabled, final boolean redact, final @Nullable Set<String> sensitiveNames) {
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
    }

    /** 默认记录摘要并启用查询参数脱敏。 */
    public static HttpLogOptions defaults() {
        return new HttpLogOptions(true, true, Set.of());
    }
}
