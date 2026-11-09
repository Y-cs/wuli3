package com.kjs.wuli3.aliyun.autoconfigure;

import com.kjs.wuli3.aliyun.oss.OssClientManager;
import com.kjs.wuli3.aliyun.sts.DefaultStsPolicyProvider;
import com.kjs.wuli3.aliyun.sts.StsPolicyProvider;
import com.kjs.wuli3.aliyun.sts.StsTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 根据命名配置创建 AliYun OSS 客户端和 STS 临时凭据模板。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@AutoConfiguration
@EnableConfigurationProperties(AliYunProperties.class)
public class AliYunAutoConfiguration {

    /** 创建由 Spring 容器统一管理生命周期的 OSS 客户端管理器。 */
    @Bean
    @ConditionalOnMissingBean
    OssClientManager ossClientManager(final AliYunProperties properties) {
        return OssClientManager.create(properties);
    }

    /** 默认按 bucket 生成 STS 会话策略，应用可注册同类型 Bean 覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    StsPolicyProvider stsPolicyProvider() {
        return new DefaultStsPolicyProvider();
    }

    /** 仅在配置了 STS roleArn 时创建临时凭据模板。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wuli3.aliyun.sts", name = "role-arn")
    StsTemplate stsTemplate(final AliYunProperties properties, final StsPolicyProvider policyProvider) {
        return StsTemplate.create(properties.getSts(), policyProvider);
    }
}
