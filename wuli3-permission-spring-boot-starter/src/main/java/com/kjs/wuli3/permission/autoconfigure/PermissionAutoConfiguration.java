package com.kjs.wuli3.permission.autoconfigure;

import com.kjs.wuli3.permission.PermissionChecker;
import com.kjs.wuli3.permission.PermissionResolver;
import com.kjs.wuli3.permission.annotation.Permission;
import com.kjs.wuli3.permission.internal.PermissionAdvisor;
import com.kjs.wuli3.permission.internal.PermissionDeclarationIndex;
import com.kjs.wuli3.permission.internal.PermissionMetadata;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.scan.PermissionResourceScanner;
import com.kjs.wuli3.permission.spi.PrincipalPermissionLoader;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.spring.autoconfigure.ApplicationServiceProperties;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 配置权限目录、主体适配及方法拦截；缺少授权提供器时拒绝启动，防止静默失效。
 *
 * @author 国杨 create on 2026/10/9 10:00
 */
@AutoConfiguration(after = CoreSpringBootAutoConfiguration.class)
@EnableConfigurationProperties(PermissionProperties.class)
@Import(PermissionAutoConfiguration.ProxyRegistrar.class)
public class PermissionAutoConfiguration {

    // 默认存储和声明索引共享一次扫描；延迟执行，避免 Advisor 装配时提前创建业务依赖。
    private @Nullable List<Class<?>> scannedTypes;

    /** 扫描应用根包并合并注册的 Bean 类型；包括懒加载 Bean 的已知类型。 */
    private synchronized List<Class<?>> permissionScan(
            final PermissionProperties properties, final ConfigurableListableBeanFactory factory) {
        if (this.scannedTypes != null) {
            return this.scannedTypes;
        }
        final ClassLoader loader = factory.getBeanClassLoader();
        final PermissionResourceScanner extractor = new PermissionResourceScanner(
                loader == null ? PermissionAutoConfiguration.class.getClassLoader() : loader);
        // 显式配置替换默认应用根包；包扫描也能发现未注册成 Bean 的权限枚举。
        final List<String> packages = properties.getBasePackages().isEmpty()
                ? (AutoConfigurationPackages.has(factory) ? AutoConfigurationPackages.get(factory) : List.of())
                : properties.getBasePackages();
        final List<Class<?>> types = new ArrayList<>();
        if (!packages.isEmpty()) {
            types.addAll(extractor.scanTypes(packages.toArray(String[]::new)));
        }
        // 补充容器已知的目标类型，使扫描包之外的 Bean 权限声明也能参与校验。
        for (final String name : factory.getBeanDefinitionNames()) {
            final Class<?> type = AutoProxyUtils.determineTargetClass(factory, name);
            if (type != null && !type.getName().startsWith("org.springframework.")) {
                types.add(type);
            }
        }
        this.scannedTypes = List.copyOf(types);
        return this.scannedTypes;
    }

    /** 将扫描发现的资源注册到本地存储，允许应用提供自己的资源存储。 */
    @Bean
    @ConditionalOnMissingBean
    LocalPermissionStore localPermissionStore(
            final PermissionProperties properties,
            final ConfigurableListableBeanFactory factory,
            final ApplicationServiceProperties serviceProperties) {
        if (serviceProperties.getServiceCode().isBlank()) {
            throw new IllegalStateException("权限资源隔离要求配置 application.service.service-code 或 spring.application.name");
        }
        final PermissionResourceScanner scanner = new PermissionResourceScanner(java.util.Objects.requireNonNullElse(
                factory.getBeanClassLoader(), PermissionAutoConfiguration.class.getClassLoader()));
        return new LocalPermissionStore(
                serviceProperties.getServiceCode(), scanner.resources(this.permissionScan(properties, factory)));
    }

    /** 资源注册完成后统一校验使用点；自定义存储也必须通过相同校验。 */
    @Bean
    PermissionDeclarationIndex permissionDeclarationIndex(
            final LocalPermissionStore store,
            final PermissionProperties properties,
            final ConfigurableListableBeanFactory factory) {
        final Map<Permission, PermissionKey> permissions = new HashMap<>();
        for (final Class<?> type : this.permissionScan(properties, factory)) {
            for (final Permission declaration : PermissionMetadata.declarations(type)) {
                permissions.computeIfAbsent(
                        declaration,
                        reference -> PermissionResolver.resolve(store, reference.value(), reference.resourceClass()));
            }
        }
        return new PermissionDeclarationIndex(permissions);
    }

    /** 即使开启全局懒加载，也在启动完成前验证目录及授权依赖。 */
    @Bean
    @Lazy(false)
    SmartInitializingSingleton permissionStartupValidation(
            final ObjectProvider<PermissionDeclarationIndex> declarations,
            final ObjectProvider<PermissionChecker> checkers,
            final PermissionProperties properties) {
        return () -> {
            // 普通单例初始化结束后再强制取得依赖，将懒加载中的声明和装配错误提前到启动期。
            declarations.getObject();
            if (properties.isEnforcementEnabled()) {
                checkers.getObject();
            }
        };
    }

    /** 提供代码调用及注解共用的当前主体鉴权入口。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "wuli3.permission",
            name = "enforcement-enabled",
            havingValue = "true",
            matchIfMissing = true)
    PermissionChecker permissionChecker(
            final LocalPermissionStore store, final PrincipalPermissionLoader loader, final ContextReader reader) {
        // 单例只持有服务级依赖，主体 Token 和权限快照在每次调用时获取。
        return new PermissionChecker(store, loader, reader);
    }

    /** 为带权限的方法创建授权 Advisor。 */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnProperty(
            prefix = "wuli3.permission",
            name = "enforcement-enabled",
            havingValue = "true",
            matchIfMissing = true)
    Advisor permissionAdvisor(
            final ObjectProvider<PermissionDeclarationIndex> declarations,
            final ObjectProvider<PermissionChecker> checkers) {
        // 代理基础设施创建较早，传入获取函数，避免此时扫描目录或初始化业务加载器。
        return PermissionAdvisor.create(declarations::getObject, checkers::getObject);
    }

    /** 注册 Spring 原生代理创建器，避免鉴权依赖隐式存在的 AspectJ。 */
    static final class ProxyRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(
                final @NonNull AnnotationMetadata metadata, final @NonNull BeanDefinitionRegistry registry) {
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
        }
    }
}
