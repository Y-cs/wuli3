package com.kjs.wuli3.aliyun.sts;

import com.kjs.wuli3.aliyun.profile.AccessKeyConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * STS AssumeRole 配置，用于按角色换取临时访问凭据。
 *
 * 注意：roleArn 为空时不会创建 STS 模板，因此只配置 OSS 的应用不受影响。
 *
 * @author lipt create on 2026/11/9 12:00
 */
@Getter
@Setter
public class StsConfig {

    /** 调用 AssumeRole 的访问凭据。 */
    private AccessKeyConfig accessKey = new AccessKeyConfig();

    /** 角色 ARN；为空时跳过 STS 模板。 */
    private String roleArn = "";

    /** 角色会话名称，用于审计和区分调用方。 */
    private String roleSessionName = "app";

    /** 临时凭据有效期，单位为秒；取值范围为 900 至 3600。 */
    private long durationSeconds = 3600L;

    /** 内联授权策略 JSON；非空时优先于 {@link StsPolicyProvider}。 */
    private String policy = "";

    /** 默认策略授权范围使用的 OSS Bucket；供 SPI 生成策略时使用。 */
    private String bucket = "";

    /** 默认策略授权目录，拼接在 bucket 之后；默认 uploads。 */
    private String folder = "uploads";

    /** STS 服务 endpoint，例如 sts.cn-hangzhou.aliyuncs.com。 */
    private String endpoint = "sts.cn-hangzhou.aliyuncs.com";
}
