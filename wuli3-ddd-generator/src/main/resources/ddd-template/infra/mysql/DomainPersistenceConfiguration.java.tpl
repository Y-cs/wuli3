package {{basePackage}}.infra.config;

import {{basePackage}}.infra.persistence.{{domainPackage}}.mapper.{{domainType}}Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 {{domainType}} Mapper 扫描；默认启用，无数据库的组装测试可显式关闭。
 *
 * @author GuoYang create on 2026/10/14 15:30
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "application.persistence.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackageClasses = {{domainType}}Mapper.class)
public class {{domainType}}PersistenceConfiguration {}
