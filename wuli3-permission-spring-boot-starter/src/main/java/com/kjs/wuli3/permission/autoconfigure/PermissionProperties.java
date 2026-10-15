package com.kjs.wuli3.permission.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限扫描与拦截配置。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@Data
@ConfigurationProperties("wuli3.permission")
public final class PermissionProperties {

    /** 缺省采用 Spring Boot 自动配置根包；显式配置替换默认包，应同时包含应用及所需依赖包。 */
    private List<String> basePackages = new ArrayList<>();

    /** 默认启用 Checker 和注解鉴权；关闭后仅保留资源目录，纯 Java 快照判断不受影响。 */
    private boolean enforcementEnabled = true;

    /** 返回配置的扫描包副本。 */
    public List<String> getBasePackages() {
        return List.copyOf(this.basePackages);
    }

    /** 绑定需要扫描的应用包。 */
    public void setBasePackages(final List<String> basePackages) {
        this.basePackages = new ArrayList<>(basePackages);
    }
}
