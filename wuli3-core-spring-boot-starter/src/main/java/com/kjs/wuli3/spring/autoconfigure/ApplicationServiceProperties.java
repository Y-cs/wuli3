package com.kjs.wuli3.spring.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 统一 Web 错误码、权限资源和 Redis key 使用的服务标识。
 *
 * 注意：service-code 未配置或为空白时使用 spring.application.name；两者均未配置时返回空字符串，
 * 需要服务隔离的基础设施应在装配时拒绝空标识。
 *
 * @author GuoYang create on 2026/8/17 11:53
 */
@ConfigurationProperties(prefix = "application.service")
public class ApplicationServiceProperties implements EnvironmentAware, InitializingBean {
    /** 服务标识，显式配置优先于 spring.application.name；最多 64 个字母、数字、点、下划线或连字符。 */
    @Getter
    @Setter
    private String serviceCode = "";

    private String applicationName = "";

    /** 在配置绑定前记录应用名，供空白服务标识回退使用。 */
    @Override
    public void setEnvironment(final Environment environment) {
        this.applicationName = environment.getProperty("spring.application.name", "");
    }

    /** 在配置绑定后解析并校验最终标识；无需依赖 Web 或 Bean Validation。 */
    @Override
    public void afterPropertiesSet() {
        if (this.serviceCode.isBlank()) {
            this.serviceCode = this.applicationName.isBlank() ? "" : this.applicationName;
        }
        if (!this.serviceCode.isEmpty() && !this.serviceCode.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalStateException(
                    "application.service.service-code（空白时取 spring.application.name）必须为最多 64 个字母、数字、'.'、'_' 或 '-'");
        }
    }
}
