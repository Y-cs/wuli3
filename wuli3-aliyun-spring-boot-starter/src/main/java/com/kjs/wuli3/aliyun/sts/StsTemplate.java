package com.kjs.wuli3.aliyun.sts;

import com.aliyun.sts20150401.Client;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.kjs.wuli3.aliyun.error.StsErrorCode;
import com.kjs.wuli3.aliyun.error.StsOperateException;
import com.kjs.wuli3.core.assertion.Asserts;
import com.kjs.wuli3.core.error.ErrorCodeException;
import java.time.Instant;

/**
 * 面向单个 STS 角色的 AssumeRole 操作模板，统一转换 SDK 异常。
 *
 * 注意：模板持有可复用的 SDK 客户端，可被多线程并发调用。
 *
 * @author lipt create on 2026/11/9 12:00
 */
public final class StsTemplate {

    private static final long MIN_DURATION_SECONDS = 900L;
    private static final long MAX_DURATION_SECONDS = 3600L;

    private final StsConfig config;
    private final StsPolicyProvider policyProvider;
    private final Client client;

    private StsTemplate(final StsConfig config, final StsPolicyProvider policyProvider, final Client client) {
        this.config = config;
        this.policyProvider = policyProvider;
        this.client = client;
    }

    /** 校验配置并创建 STS 模板。 */
    public static StsTemplate create(final StsConfig config, final StsPolicyProvider policyProvider) {
        StsTemplate.validate(config);
        final Config sdkConfig = new Config()
                .setAccessKeyId(config.getAccessKey().getId())
                .setAccessKeySecret(config.getAccessKey().getSecret())
                .setEndpoint(config.getEndpoint());
        try {
            return new StsTemplate(config, policyProvider, new Client(sdkConfig));
        } catch (final Exception exception) {
            throw new StsOperateException("create STS client failed", exception);
        }
    }

    /**
     * 调用 AssumeRole 获取临时访问凭据。
     *
     * @return 临时凭据及其过期时间
     * @throws ErrorCodeException 请求失败或响应缺少必要字段时
     */
    public StsCredentials getCredentials() {
        try {
            final AssumeRoleResponse response = this.client.assumeRoleWithOptions(
                    StsTemplate.buildRequest(this.config, this.resolvePolicy()), new RuntimeOptions());
            return StsTemplate.toCredentials(response);
        } catch (final ErrorCodeException exception) {
            throw exception;
        } catch (final Exception exception) {
            throw new StsOperateException(exception);
        }
    }

    /** 配置的 policy 优先；否则交给策略扩展点生成。 */
    String resolvePolicy() {
        if (!this.config.getPolicy().isBlank()) {
            return this.config.getPolicy();
        }
        return this.policyProvider.resolve(this.config);
    }

    /** 按配置构造 AssumeRole 请求；策略为空时不发送 policy 字段。 */
    static AssumeRoleRequest buildRequest(final StsConfig config, final String policy) {
        final AssumeRoleRequest request = new AssumeRoleRequest()
                .setRoleArn(config.getRoleArn())
                .setRoleSessionName(config.getRoleSessionName())
                .setDurationSeconds(config.getDurationSeconds());
        if (!policy.isBlank()) {
            request.setPolicy(policy);
        }
        return request;
    }

    /** 将 SDK 响应转换为凭据对象；缺少必要字段时抛出统一错误码。 */
    static StsCredentials toCredentials(final AssumeRoleResponse response) {
        final AssumeRoleResponseBody body = Asserts.requireNonNull(response.getBody(), StsErrorCode.INVALID_RESPONSE);
        final AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                Asserts.requireNonNull(body.getCredentials(), StsErrorCode.INVALID_RESPONSE);
        final String expiration = Asserts.requireNonNull(credentials.getExpiration(), StsErrorCode.INVALID_RESPONSE);
        return new StsCredentials(
                Asserts.requireNonNull(credentials.getAccessKeyId(), StsErrorCode.INVALID_RESPONSE),
                Asserts.requireNonNull(credentials.getAccessKeySecret(), StsErrorCode.INVALID_RESPONSE),
                Asserts.requireNonNull(credentials.getSecurityToken(), StsErrorCode.INVALID_RESPONSE),
                Instant.parse(expiration));
    }

    private static void validate(final StsConfig config) {
        if (config.getRoleArn().isBlank()) {
            throw new ErrorCodeException(StsErrorCode.INVALID_CONFIG, "AliYun STS roleArn must not be blank");
        }
        if (config.getRoleSessionName().isBlank()) {
            throw new ErrorCodeException(StsErrorCode.INVALID_CONFIG, "AliYun STS roleSessionName must not be blank");
        }
        if (config.getDurationSeconds() < MIN_DURATION_SECONDS || config.getDurationSeconds() > MAX_DURATION_SECONDS) {
            throw new ErrorCodeException(
                    StsErrorCode.INVALID_CONFIG, "AliYun STS durationSeconds must be between 900 and 3600");
        }
    }
}
