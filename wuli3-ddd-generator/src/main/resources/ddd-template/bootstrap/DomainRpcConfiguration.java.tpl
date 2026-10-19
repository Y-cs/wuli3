package {{basePackage}}.config;

import {{basePackage}}.api.facade.{{domainType}}Facade;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在启动边界发布内部 Facade，应用实现不依赖 RPC 框架。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "dubbo", name = "enabled", matchIfMissing = true)
public class {{domainType}}RpcConfiguration {
    /** 复用 Spring 管理的业务实现，由 Dubbo 生命周期负责导出和关闭。 */
    @Bean
    public ServiceBean<{{domainType}}Facade> {{domainPackage}}RpcService(final {{domainType}}Facade facade, final ApplicationContext context) {
        final ServiceBean<{{domainType}}Facade> service = new ServiceBean<>(context);
        service.setInterface({{domainType}}Facade.class);
        service.setRef(facade);
        return service;
    }
}
