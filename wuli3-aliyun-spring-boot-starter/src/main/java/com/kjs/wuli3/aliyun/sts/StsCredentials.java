package com.kjs.wuli3.aliyun.sts;

import java.time.Instant;

/**
 * STS AssumeRole 返回的临时访问凭据及上传目标信息。
 *
 * @param accessKeyId 临时 AccessKeyId
 * @param accessKeySecret 临时 AccessKeySecret
 * @param securityToken 临时安全令牌
 * @param expiration 凭据过期时间
 * @param bucket 临时凭据授权的 OSS Bucket
 * @param endpoint OSS 访问 endpoint
 * @param directory 本次授权目录，对象 key 应位于该目录下
 * @param prefixUrl 对象访问前缀 URL
 * @author lipt create on 2026/11/9 12:00
 */
public record StsCredentials(
        String accessKeyId,
        String accessKeySecret,
        String securityToken,
        Instant expiration,
        String bucket,
        String endpoint,
        String directory,
        String prefixUrl) {}
