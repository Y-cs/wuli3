package com.kjs.wuli3.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.ContextManager;
import com.kjs.wuli3.propagation.accessor.AuthContextAccessor;
import com.kjs.wuli3.propagation.accessor.InvocationContextAccessor;
import com.kjs.wuli3.propagation.codec.ContextPropagator;
import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.InvocationContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.propagation.store.ContextBinder;
import com.kjs.wuli3.propagation.store.ContextReader;
import com.kjs.wuli3.propagation.store.ThreadLocalContextBackend;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证共享上下文组件独立装配、应用覆盖和作用域一致性。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
class ContextAutoConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ContextAutoConfiguration.class));

    /** 验证共享组件可自动发现，读取、绑定和访问器使用相同作用域。 */
    @Test
    void registersSharedScopeWithStableBeanNames() {
        assertThat(ImportCandidates.load(
                        AutoConfiguration.class, this.getClass().getClassLoader()))
                .contains(ContextAutoConfiguration.class.getName());
        this.runner.run(context -> {
            assertThat(context).hasSingleBean(ContextReader.class).hasSingleBean(ContextBinder.class);
            assertThat(context.getBean("contextReader")).isSameAs(context.getBean(ContextBinder.class));
            assertThat(context.getBean("contextManager")).isSameAs(context.getBean(ContextManager.class));
            assertThat(context.getBean("contextPropagator")).isSameAs(context.getBean(ContextPropagator.class));
            final InvocationContextAccessor invocation = context.getBean(InvocationContextAccessor.class);
            final AuthContextAccessor auth = context.getBean(AuthContextAccessor.class);
            context.getBean(ContextManager.class)
                    .with(
                            new InvocationContext("127.0.0.1", "shared-request"),
                            new AuthContext(PrincipalType.CUSTOMER, "42"))
                    .run(() -> {
                        assertThat(invocation.requestId()).contains("shared-request");
                        assertThat(auth.principalId()).contains("42");
                    });
            assertThat(invocation.current()).isEmpty();
            assertThat(auth.current()).isEmpty();
        });
    }

    /** 验证应用提供的组件不会被默认组件覆盖。 */
    @Test
    void honorsCustomComponents() {
        final ThreadLocalContextBackend backend = new ThreadLocalContextBackend();
        final ContextManager manager = new ContextManager(backend, backend);
        final ContextPropagator propagator = new ContextPropagator(ContextPropagator.standardContextEncoder());
        final AuthContextAccessor auth = new AuthContextAccessor(backend);
        final InvocationContextAccessor invocation = new InvocationContextAccessor(backend);
        this.runner
                .withBean("contextReader", ThreadLocalContextBackend.class, () -> backend)
                .withBean("contextManager", ContextManager.class, () -> manager)
                .withBean("contextPropagator", ContextPropagator.class, () -> propagator)
                .withBean(AuthContextAccessor.class, () -> auth)
                .withBean(InvocationContextAccessor.class, () -> invocation)
                .run(context -> {
                    assertThat(context).hasSingleBean(ContextReader.class).hasSingleBean(ContextBinder.class);
                    assertThat(context).hasSingleBean(ContextManager.class).hasSingleBean(ContextPropagator.class);
                    assertThat(context)
                            .hasSingleBean(AuthContextAccessor.class)
                            .hasSingleBean(InvocationContextAccessor.class);
                    assertThat(context.getBean(ContextReader.class)).isSameAs(backend);
                    assertThat(context.getBean(ContextManager.class)).isSameAs(manager);
                    assertThat(context.getBean(ContextPropagator.class)).isSameAs(propagator);
                    assertThat(context.getBean(AuthContextAccessor.class)).isSameAs(auth);
                    assertThat(context.getBean(InvocationContextAccessor.class)).isSameAs(invocation);
                });
    }
}
