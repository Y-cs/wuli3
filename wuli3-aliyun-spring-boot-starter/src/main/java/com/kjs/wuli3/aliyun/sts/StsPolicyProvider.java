package com.kjs.wuli3.aliyun.sts;

/**
 * STS 会话策略扩展点，用于生成 AssumeRole 的内联授权策略。
 *
 * 注意：`wuli3.aliyun.sts.policy` 非空时优先使用配置值，本接口不会被调用。
 * 实现可注册为 Spring Bean 覆盖默认实现，例如按日期目录或调用方动态生成策略。
 *
 * @author lipt create on 2026/11/9 12:00
 */
@FunctionalInterface
public interface StsPolicyProvider {

    /**
     * 生成内联授权策略 JSON。
     *
     * @param config 当前 STS 配置，包含 bucket 等扩展信息
     * @return 策略 JSON；返回空字符串表示不发送内联策略
     */
    String resolve(StsConfig config);
}
