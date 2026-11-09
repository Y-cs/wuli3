package com.kjs.wuli3.aliyun.sts;

import java.time.Instant;

/**
 * STS AssumeRole 返回的临时访问凭据。
 *
 * @param accessKeyId 临时 AccessKeyId
 * @param accessKeySecret 临时 AccessKeySecret
 * @param securityToken 临时安全令牌
 * @param expiration 凭据过期时间
 * @author lipt create on 2026/11/9 12:00
 */
public record StsCredentials(String accessKeyId, String accessKeySecret, String securityToken, Instant expiration) {}
