package com.kjs.wuli3.permission.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.PermissionChecker;
import com.kjs.wuli3.permission.lifecyclefixture.LifecycleFixture.Contract;
import com.kjs.wuli3.permission.lifecyclefixture.LifecycleFixture.ProtectedService;
import com.kjs.wuli3.permission.lifecyclefixture.LifecycleFixture.ResourcePermission;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.spi.PrincipalPermissionLoader;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证工厂产品与懒加载实现类的扫描和代理鉴权。
 *
 * @author 国杨 create on 2026/10/14 10:00
 */
class PermissionBeanLifecycleTest {
    private final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();

    @Test
    void rejectsUnscannedFactoryDeclarationOnInvocation() {
        this.runner()
                .withBean(
                        "product", UnknownProductFactory.class, () -> new UnknownProductFactory(new ProtectedService()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final Contract service = (Contract) context.getBean("product");
                    assertThatThrownBy(service::read)
                            .isInstanceOf(ErrorCodeException.class)
                            .hasMessageContaining("启动扫描");
                });
    }

    @Test
    void scannedFactoryProductRequiresCurrentPrincipalPermissions() {
        this.scanningRunner()
                .withBean(
                        "product", UnknownProductFactory.class, () -> new UnknownProductFactory(new ProtectedService()))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final Contract service = (Contract) context.getBean("product");
                    assertThatThrownBy(service::read).isInstanceOf(ErrorCodeException.class);
                    this.auth("deny", () -> assertThatThrownBy(service::read).isInstanceOf(ErrorCodeException.class));
                    this.auth("allow", () -> assertThat(service.read()).isEqualTo("protected"));
                });
    }

    @Test
    void rejectsUnscannedLazyDeclarationOnInvocation() {
        this.runner()
                .withBean("service", Contract.class, ProtectedService::new, definition -> definition.setLazyInit(true))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final Contract service = context.getBean(Contract.class);
                    assertThatThrownBy(service::read)
                            .isInstanceOf(ErrorCodeException.class)
                            .hasMessageContaining("启动扫描");
                });
    }

    @Test
    void scannedLazyImplementationSupportsAuthorization() {
        this.scanningRunner()
                .withBean("service", Contract.class, ProtectedService::new, definition -> definition.setLazyInit(true))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final Contract service = context.getBean(Contract.class);
                    assertThatThrownBy(service::read).isInstanceOf(ErrorCodeException.class);
                    this.auth("deny", () -> assertThatThrownBy(service::read).isInstanceOf(ErrorCodeException.class));
                    this.auth("allow", () -> assertThat(service.read()).isEqualTo("protected"));
                });
    }

    @Test
    void disablingEnforcementOnlyKeepsResourceDirectory() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, PermissionAutoConfiguration.class))
                .withPropertyValues("spring.application.name=order-service")
                .withPropertyValues(
                        "wuli3.permission.enforcement-enabled=false",
                        "wuli3.permission.base-packages[0]=com.kjs.wuli3.permission.lifecyclefixture")
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(LocalPermissionStore.class)
                            .doesNotHaveBean(PermissionChecker.class)
                            .doesNotHaveBean("permissionAdvisor")
                            .doesNotHaveBean("permissionBeanValidator");
                    assertThat(context.getBean(LocalPermissionStore.class).requirement())
                            .containsExactlyEntriesOf(Map.of("order-service:LIFECYCLE", 8L));
                });
    }

    private ApplicationContextRunner scanningRunner() {
        return this.runner()
                .withPropertyValues("wuli3.permission.base-packages[0]=com.kjs.wuli3.permission.lifecyclefixture");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, PermissionAutoConfiguration.class))
                .withPropertyValues("spring.application.name=order-service")
                .withBean(LocalPermissionStore.class, () -> new LocalPermissionStore(List.of(ResourcePermission.class)))
                .withBean(ContextReader.class, () -> this.backend)
                .withBean(
                        PrincipalPermissionLoader.class,
                        () -> (token, resources) -> new PrincipalPermissions(
                                token, Map.of("LIFECYCLE", token.token().equals("allow") ? 8L : 0L)));
    }

    private void auth(final String token, final Runnable action) {
        this.backend.run(ContextState.of(new AuthContext(PrincipalType.ADMIN, "1", token)), action);
    }

    static class UnknownProductFactory implements FactoryBean<Object> {
        private final Object product;

        UnknownProductFactory(final Object product) {
            this.product = product;
        }

        @Override
        public Object getObject() {
            return this.product;
        }

        @Override
        public @Nullable Class<?> getObjectType() {
            return null;
        }
    }
}
