package com.kjs.wuli3.aliyun.sts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.kjs.wuli3.aliyun.error.StsErrorCode;
import com.kjs.wuli3.core.error.ErrorCodeException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StsTemplateTest {

    private static final StsPolicyProvider POLICY_PROVIDER = (config, directory) -> "";

    @Test
    void rejectsBlankRoleArn() {
        assertThatThrownBy(() -> StsTemplate.create(new StsConfig(), POLICY_PROVIDER))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("AliYun STS roleArn must not be blank");
    }

    @Test
    void rejectsBlankRoleSessionName() {
        final StsConfig config = validConfig();
        config.setRoleSessionName(" ");

        assertThatThrownBy(() -> StsTemplate.create(config, POLICY_PROVIDER))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("AliYun STS roleSessionName must not be blank");
    }

    @Test
    void rejectsDurationOutOfRange() {
        final StsConfig config = validConfig();
        config.setDurationSeconds(100L);

        assertThatThrownBy(() -> StsTemplate.create(config, POLICY_PROVIDER))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage("AliYun STS durationSeconds must be between 900 and 3600");
    }

    @Test
    void prefersConfigPolicyOverProvider() {
        final StsConfig config = validConfig();
        config.setPolicy("config-policy");

        final StsTemplate template = StsTemplate.create(config, (ignored, directory) -> "provider-policy");

        assertThat(template.resolvePolicy("uploads/2026/11/9/12")).isEqualTo("config-policy");
    }

    @Test
    void usesProviderPolicyWhenConfigPolicyBlank() {
        final StsConfig config = validConfig();
        config.setPolicy("");

        final StsTemplate template = StsTemplate.create(config, (ignored, directory) -> "provider-policy");

        assertThat(template.resolvePolicy("uploads/2026/11/9/12")).isEqualTo("provider-policy");
    }

    @Test
    void buildsDirectoryFromFolderAndCurrentHour() {
        final StsConfig config = new StsConfig();
        config.setFolder("/uploads");

        final String directory = StsTemplate.buildDirectory(config, LocalDateTime.of(2026, 11, 9, 12, 30));

        assertThat(directory).isEqualTo("uploads/202611/9/12");
    }

    @Test
    void defaultProviderScopesPolicyToDirectory() {
        final StsConfig config = new StsConfig();
        config.setBucket("my-bucket");

        final String policy = new DefaultStsPolicyProvider().resolve(config, "uploads/2026/11/9/12");

        assertThat(policy).contains("acs:oss:*:*:my-bucket/uploads/2026/11/9/12*");
    }

    @Test
    void defaultProviderReturnsBlankWithoutBucket() {
        assertThat(new DefaultStsPolicyProvider().resolve(new StsConfig(), "uploads/2026/11/9/12"))
                .isEmpty();
    }

    @Test
    void buildsRequestWithPolicyOnlyWhenPresent() {
        final StsConfig config = validConfig();

        final AssumeRoleRequest withoutPolicy = StsTemplate.buildRequest(config, "");
        final AssumeRoleRequest withPolicy = StsTemplate.buildRequest(config, "{\"Version\":\"1\"}");

        assertThat(withoutPolicy.getDurationSeconds()).isEqualTo(1800L);
        assertThat(withoutPolicy.getPolicy()).isNull();
        assertThat(withPolicy.getPolicy()).isEqualTo("{\"Version\":\"1\"}");
    }

    @Test
    void mapsCredentialsAndUploadTargetFromResponse() {
        final AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                new AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials()
                        .setAccessKeyId("ak")
                        .setAccessKeySecret("sk")
                        .setSecurityToken("token")
                        .setExpiration("2026-11-09T13:00:00Z");
        final AssumeRoleResponse response =
                new AssumeRoleResponse().setBody(new AssumeRoleResponseBody().setCredentials(credentials));
        final StsConfig config = new StsConfig();
        config.setBucket("my-bucket");
        config.setPrefixUrl("https://cdn.example.com");

        final StsCredentials result = StsTemplate.toCredentials(response, config, "uploads/2026/11/9/12");

        assertThat(result.accessKeyId()).isEqualTo("ak");
        assertThat(result.accessKeySecret()).isEqualTo("sk");
        assertThat(result.securityToken()).isEqualTo("token");
        assertThat(result.expiration()).isEqualTo(Instant.parse("2026-11-09T13:00:00Z"));
        assertThat(result.bucket()).isEqualTo("my-bucket");
        assertThat(result.endpoint()).isEqualTo("oss-cn-hangzhou.aliyuncs.com");
        assertThat(result.directory()).isEqualTo("uploads/2026/11/9/12");
        assertThat(result.prefixUrl()).isEqualTo("https://cdn.example.com");
    }

    @Test
    void rejectsResponseWithoutCredentials() {
        assertThatThrownBy(() -> StsTemplate.toCredentials(new AssumeRoleResponse(), new StsConfig(), "uploads"))
                .isInstanceOf(ErrorCodeException.class)
                .hasMessage(StsErrorCode.INVALID_RESPONSE.getMessage());
    }

    private static StsConfig validConfig() {
        final StsConfig config = new StsConfig();
        config.setRoleArn("acs:ram::1:role/uploader");
        config.setDurationSeconds(1800L);
        return config;
    }
}
