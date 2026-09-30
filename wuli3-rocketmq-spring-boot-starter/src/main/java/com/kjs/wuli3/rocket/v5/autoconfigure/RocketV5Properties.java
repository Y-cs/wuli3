package com.kjs.wuli3.rocket.v5.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ Java Client v5 Producer 的基础连接配置。
 *
 * <p>认证、事务检查器和多 Producer 场景可通过业务自定义 {@code Producer} Bean 接管。
 *
 * @author GuoYang create on 2026/9/30 10:00
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wuli3.rocketmq.v5")
public class RocketV5Properties {

    /** RocketMQ Proxy endpoint，例如 localhost:8081。 */
    private @Nullable String endpoints;

    /** 访问密钥；建议通过环境变量占位符注入。 */
    private @Nullable String accessKey;

    /** 访问密钥；建议通过环境变量占位符注入。 */
    private @Nullable String accessSecret;

    /** 临时安全令牌；长期密钥不需要配置。 */
    private @Nullable String securityToken;

    /** Producer 预声明的主题列表。 */
    private List<String> topics = new ArrayList<>();

    /** 客户端请求超时时间。 */
    private Duration requestTimeout = Duration.ofSeconds(10);

    /** 是否启用 TLS。 */
    private boolean sslEnabled;

    /** 可选命名空间。 */
    private @Nullable String namespace;

    /** 客户端启动最大重试次数。 */
    private int maxStartupAttempts = 3;

    /** Producer 单次发送最大尝试次数。 */
    private int maxAttempts = 3;
}
