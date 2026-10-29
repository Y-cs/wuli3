package com.kjs.wuli3.openfeign.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置 Wuli3 OpenFeign 上下文和错误传播组件。
 *
 * @author lipt create on 2026/10/29 18:21
 */
@Getter
@ConfigurationProperties(prefix = "wuli3.openfeign")
public class OpenFeignProperties {
    /** 上下文传播开关；默认启用，控制出站请求头写入。 */
    private final Feature context = new Feature();

    /** 错误传播开关；默认启用，控制非 2xx 响应解码。 */
    private final Feature error = new Feature();

    /** 表示一项 OpenFeign 传播能力的开关配置。 */
    @Getter
    @Setter
    public static class Feature {
        /** 是否启用对应传播能力；默认启用。 */
        private boolean enabled = true;
    }
}
