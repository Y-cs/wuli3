package com.kjs.wuli3.spring.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 标记公共 Spring Boot 基础设施自动配置边界。
 *
 * @author GuoYang create on 2026/9/3 14:55
 */
@AutoConfiguration
@EnableConfigurationProperties(ApplicationServiceProperties.class)
public class CoreSpringBootAutoConfiguration {}
