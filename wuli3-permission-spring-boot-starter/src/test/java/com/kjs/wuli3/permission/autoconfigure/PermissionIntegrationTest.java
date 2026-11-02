package com.kjs.wuli3.permission.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kjs.wuli3.core.error.ErrorCodeException;
import com.kjs.wuli3.permission.PermissionChecker;
import com.kjs.wuli3.permission.PermissionResolver;
import com.kjs.wuli3.permission.annotation.Permission;
import com.kjs.wuli3.permission.annotation.PermissionResource;
import com.kjs.wuli3.permission.internal.PermissionAdvisor;
import com.kjs.wuli3.permission.internal.PermissionDeclarationIndex;
import com.kjs.wuli3.permission.internal.PermissionMetadata;
import com.kjs.wuli3.permission.model.ActionCode;
import com.kjs.wuli3.permission.model.PermissionCode;
import com.kjs.wuli3.permission.model.PermissionKey;
import com.kjs.wuli3.permission.model.PrincipalPermissions;
import com.kjs.wuli3.permission.scan.PermissionResourceScanner;
import com.kjs.wuli3.permission.spi.PrincipalPermissionLoader;
import com.kjs.wuli3.permission.store.LocalPermissionStore;
import com.kjs.wuli3.propagation.context.ContextState;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import com.kjs.wuli3.spring.autoconfigure.CoreSpringBootAutoConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证资源扫描、引用解析和逐次加载主体权限的 Spring 集成边界。
 * @author 国杨 create on 2026/10/13 10:00
 */
class PermissionIntegrationTest {
    private final PermissionResourceScanner scanner =
            new PermissionResourceScanner(this.getClass().getClassLoader());
    private final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();

    @Test
    void explicitServiceCodeScopesTheDirectoryAndAuthorization() {
        this.runner()
                .withPropertyValues("application.service.service-code=billing")
                .withBean(OrderService.class)
                .withBean(PrincipalPermissionLoader.class, () -> (token, resources) -> {
                    assertThat(resources).containsExactlyEntriesOf(Map.of("billing:ORDER", Long.MIN_VALUE | 24L));
                    return new PrincipalPermissions(token, Map.of("billing:ORDER", 24L));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LocalPermissionStore.class)
                                    .resources()
                                    .getFirst()
                                    .resourceCode())
                            .isEqualTo("billing:ORDER");
                    this.auth(
                            "allow",
                            () -> assertThat(context.getBean(Contract.class).approve())
                                    .isEqualTo("approved"));
                });
    }

    @Test
    void blankServiceCodeUsesApplicationNameForTheDirectory() {
        this.runner()
                .withPropertyValues(
                        "application.service.service-code=   ", "wuli3.permission.enforcement-enabled=false")
                .withBean(OrderService.class)
                .run(context -> assertThat(
                                context.getBean(LocalPermissionStore.class).requirement())
                        .containsEntry("order-service:ORDER", Long.MIN_VALUE | 24L));
    }

    @Test
    void missingServiceIdentityFailsEvenWhenOnlyBuildingTheDirectory() {
        this.runner()
                .withPropertyValues("spring.application.name=", "wuli3.permission.enforcement-enabled=false")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("spring.application.name"));
    }

    @Test
    void scansNonBeanEnumsAndExplicitExternalReferences() {
        final LocalPermissionStore store =
                new LocalPermissionStore(this.scanner.resources(List.of(OrderService.class)));
        assertThat(store.requirement()).containsEntry("ORDER", Long.MIN_VALUE | 24L);
        assertThat(store.resources().getFirst().enumClass()).isEqualTo(OrderPermission.class);
        this.runner()
                .withPropertyValues(
                        "wuli3.permission.enforcement-enabled=false",
                        "wuli3.permission.base-packages[0]=com.kjs.wuli3.permission.fixture")
                .run(context -> assertThat(context.getBean(LocalPermissionStore.class)
                                .resources()
                                .getFirst()
                                .permissions())
                        .hasSize(2));
        assertThatThrownBy(() -> this.scanner.scanTypes(" ")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.scanner.scanTypes("a.*")).isInstanceOf(ErrorCodeException.class);
        assertThatThrownBy(() -> this.scanner.scanTypes()).isInstanceOf(ErrorCodeException.class);
    }

    @Test
    void startupValidatesReferencesAndRequiredLoader() {
        this.runner().run(context -> assertThat(context).hasFailed());
        for (final Class<?> invalid : List.of(UnknownResource.class, UnknownConstant.class, MixedReference.class)) {
            this.runner()
                    .withBean("invalid", invalid)
                    .withBean(
                            PrincipalPermissionLoader.class,
                            () -> (token, resources) -> new PrincipalPermissions(token, Map.of()))
                    .run(context -> assertThat(context).hasFailed());
        }
        this.runner()
                .withBean(OrderService.class)
                .withPropertyValues("wuli3.permission.enforcement-enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("permissionAdvisor"));
    }

    @Test
    void missingContextReaderFailsAtStartupAndUnprotectedCallsDoNotLoadPermissions() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, PermissionAutoConfiguration.class))
                .withPropertyValues("spring.application.name=order-service")
                .withBean(
                        PrincipalPermissionLoader.class,
                        () -> (token, resources) -> new PrincipalPermissions(token, Map.of()))
                .run(context -> assertThat(context).hasFailed());
        final AtomicInteger loads = new AtomicInteger();
        final GenericService service = (GenericService) this.proxy(new GenericService(), true, (token, resources) -> {
            loads.incrementAndGet();
            return new PrincipalPermissions(token, Map.of());
        });
        assertThat(service.publicRead()).isEqualTo("public");
        assertThat(loads).hasValue(0);
    }

    @Test
    void bothProxyStylesCombineClassInterfaceAndMethodRequirements() {
        for (final boolean classProxy : List.of(false, true)) {
            final AtomicLong mask = new AtomicLong(16L);
            final Contract service = (Contract) this.proxy(
                    new OrderService(),
                    classProxy,
                    (token, resources) -> new PrincipalPermissions(token, Map.of("ORDER", mask.get())));
            this.auth("token", () -> {
                assertThatThrownBy(service::approve).isInstanceOf(ErrorCodeException.class);
                mask.set(24L);
                assertThat(service.approve()).isEqualTo("approved");
            });
        }
    }

    @Test
    void autoConfigurationDoesNotCreateDependencyCycleAndNeverCachesPrincipalPermissions() {
        final AtomicInteger loads = new AtomicInteger();
        this.runner()
                .withBean(OrderService.class)
                .withBean(PrincipalPermissionLoader.class, () -> (token, resources) -> {
                    loads.incrementAndGet();
                    assertThat(resources).containsEntry("order-service:ORDER", Long.MIN_VALUE | 24L);
                    return new PrincipalPermissions(
                            token, Map.of("order-service:ORDER", token.token().equals("allow") ? 24L : 0L));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final Contract service = context.getBean(Contract.class);
                    assertThatThrownBy(service::approve).isInstanceOf(ErrorCodeException.class);
                    this.auth("allow", () -> assertThat(service.approve()).isEqualTo("approved"));
                    this.auth(
                            "deny", () -> assertThatThrownBy(service::approve).isInstanceOf(ErrorCodeException.class));
                    this.auth("allow", () -> assertThat(service.approve()).isEqualTo("approved"));
                    assertThat(loads).hasValue(3);
                });
    }

    @Test
    @SuppressWarnings("NullAway") // 故意模拟违反非空契约的外部加载器。
    void rejectsMismatchedSnapshotAndPropagatesLoaderFailure() {
        final Contract mismatch = (Contract) this.proxy(
                new OrderService(),
                false,
                (token, resources) -> new PrincipalPermissions(
                        new com.kjs.wuli3.permission.model.PermissionToken("other"), Map.of("ORDER", -1L)));
        this.auth("token", () -> assertThatThrownBy(mismatch::approve).isInstanceOf(IllegalStateException.class));
        final IllegalStateException failure = new IllegalStateException("loader unavailable");
        final Contract failed = (Contract) this.proxy(new OrderService(), false, (token, resources) -> {
            throw failure;
        });
        this.auth("token", () -> assertThatThrownBy(failed::approve).isSameAs(failure));
        final Contract empty = (Contract) this.proxy(new OrderService(), false, (token, resources) -> null);
        this.auth("token", () -> assertThatThrownBy(empty::approve).isInstanceOf(IllegalStateException.class));
    }

    @Test
    void genericBridgeAndInheritedOverloadsKeepRequirements() {
        final GenericService generic = (GenericService)
                this.proxy(new GenericService(), true, (token, resources) -> new PrincipalPermissions(token, Map.of()));
        this.auth(
                "token", () -> assertThatThrownBy(() -> generic.save("value")).isInstanceOf(ErrorCodeException.class));
        final AtomicLong mask = new AtomicLong(8L);
        final ChildService child = (ChildService) this.proxy(
                new ChildService(),
                true,
                (token, resources) -> new PrincipalPermissions(token, Map.of("ORDER", mask.get())));
        this.auth("token", () -> {
            assertThat(child.read(1)).isEqualTo("number");
            assertThatThrownBy(() -> child.read("value")).isInstanceOf(ErrorCodeException.class);
            mask.set(24L);
            assertThat(child.read("value")).isEqualTo("value");
        });
    }

    @Test
    void customStoreStillValidatesLazyBeanDeclarationsAtStartup() {
        this.runner()
                .withBean(LocalPermissionStore.class, () -> new LocalPermissionStore(List.of(OrderPermission.class)))
                .withBean(
                        "invalid",
                        UnknownConstant.class,
                        UnknownConstant::new,
                        definition -> definition.setLazyInit(true))
                .withBean(
                        PrincipalPermissionLoader.class,
                        () -> (token, resources) -> new PrincipalPermissions(token, Map.of()))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customStoreCompilesDeclarationsAndSupportsAuthorization() {
        final LocalPermissionStore store = new LocalPermissionStore(List.of(OrderPermission.class));
        this.runner()
                .withBean(LocalPermissionStore.class, () -> store)
                .withBean(OrderService.class)
                .withBean(PrincipalPermissionLoader.class, () -> (token, resources) -> {
                    assertThat(resources).isEqualTo(store.requirement());
                    return new PrincipalPermissions(token, Map.of("ORDER", 24L));
                })
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean("permissionScan");
                    assertThat(context.getBean(LocalPermissionStore.class)).isSameAs(store);
                    this.auth(
                            "token",
                            () -> assertThat(context.getBean(Contract.class).approve())
                                    .isEqualTo("approved"));
                });
    }

    @Test
    void advisorRejectsDeclarationsAbsentFromStartupIndexBeforeLoadingPermissions() {
        final LocalPermissionStore store = new LocalPermissionStore(List.of(OrderPermission.class));
        final PermissionDeclarationIndex declarations = new PermissionDeclarationIndex(Map.of());
        final AtomicInteger loads = new AtomicInteger();
        final PermissionChecker checker = new PermissionChecker(
                store,
                (token, resources) -> {
                    loads.incrementAndGet();
                    return new PrincipalPermissions(token, Map.of("ORDER", -1L));
                },
                this.backend);
        final ProxyFactory factory = new ProxyFactory(new OrderService());
        factory.addAdvisor(PermissionAdvisor.create(() -> declarations, () -> checker));
        final Contract service = (Contract) factory.getProxy();
        this.auth(
                "token",
                () -> assertThatThrownBy(service::approve)
                        .isInstanceOf(ErrorCodeException.class)
                        .hasMessageContaining("启动扫描"));
        assertThat(loads).hasValue(0);
    }

    private Object proxy(final Object target, final boolean classProxy, final PrincipalPermissionLoader loader) {
        final LocalPermissionStore store = new LocalPermissionStore(this.scanner.resources(List.of(target.getClass())));
        final ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(classProxy);
        final Map<Permission, PermissionKey> compiled = new HashMap<>();
        for (final Permission declaration : PermissionMetadata.declarations(target.getClass())) {
            compiled.put(
                    declaration, PermissionResolver.resolve(store, declaration.value(), declaration.resourceClass()));
        }
        final PermissionDeclarationIndex declarations = new PermissionDeclarationIndex(compiled);
        factory.addAdvisor(
                PermissionAdvisor.create(() -> declarations, () -> new PermissionChecker(store, loader, this.backend)));
        return factory.getProxy();
    }

    private void auth(final String token, final Runnable action) {
        this.backend.run(ContextState.of(new AuthContext(PrincipalType.ADMIN, "1", token)), action);
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(CoreSpringBootAutoConfiguration.class, PermissionAutoConfiguration.class))
                .withPropertyValues("spring.application.name=order-service")
                .withBean(ContextReader.class, () -> this.backend);
    }

    @Permission(value = "READ", resourceClass = OrderPermission.class)
    static class ParentService {
        @Permission("ORDER:APPROVE")
        @Permission("ORDER:READ")
        public String read(final String value) {
            return value;
        }

        public String read(final int value) {
            return "number";
        }
    }

    static class ChildService extends ParentService {
        @Override
        public String read(final String value) {
            return value;
        }
    }

    @Getter
    @RequiredArgsConstructor
    @PermissionResource(value = "ORDER", description = "订单权限")
    enum OrderPermission implements PermissionCode {
        READ(ActionCode.READ, "查询"),
        APPROVE(ActionCode.ACTION_4, "审核"),
        UNUSED(ActionCode.ACTION_63, "特殊权限");
        private final ActionCode actionCode;
        private final String description;

        @Override
        public ActionCode getAction() {
            return this.actionCode;
        }
    }

    interface Contract {
        @Permission("ORDER:READ")
        String approve();
    }

    @Permission(value = "APPROVE", resourceClass = OrderPermission.class)
    static class OrderService implements Contract {
        @Override
        @Permission("ORDER:APPROVE")
        public String approve() {
            return "approved";
        }
    }

    @Permission("MISSING:READ")
    static class UnknownResource {}

    @Permission(value = "MISSING", resourceClass = OrderPermission.class)
    static class UnknownConstant {}

    @Permission(value = "ORDER:READ", resourceClass = OrderPermission.class)
    static class MixedReference {}

    interface GenericContract<T> {
        @Permission(value = "READ", resourceClass = OrderPermission.class)
        T save(T value);
    }

    static class GenericService implements GenericContract<String> {
        public String publicRead() {
            return "public";
        }

        @Override
        public String save(final String value) {
            return value;
        }
    }
}
