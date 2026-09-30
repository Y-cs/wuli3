package com.kjs.wuli3.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kjs.wuli3.propagation.internal.AuthContext;
import com.kjs.wuli3.propagation.internal.PrincipalType;
import com.kjs.wuli3.web.auth.AuthContextResolver;
import com.kjs.wuli3.web.internal.auth.TrustedHttpAuthContextResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.HandlerExceptionResolver;

class WebContextAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(
                    "handlerExceptionResolver",
                    HandlerExceptionResolver.class,
                    () -> (request, response, handler, exception) -> null)
            .withConfiguration(AutoConfigurations.of(WebContextAutoConfiguration.class));

    @Test
    void configuresTrustedHttpAuthContextResolverByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthContextResolver.class);
            assertThat(context.getBean(AuthContextResolver.class)).isInstanceOf(TrustedHttpAuthContextResolver.class);
        });
    }

    @Test
    void customAuthContextResolverReplacesDefault() {
        final AuthContextResolver customResolver =
                request -> Optional.of(new AuthContext(PrincipalType.CUSTOMER, "42", "alice"));

        this.contextRunner
                .withBean(AuthContextResolver.class, () -> customResolver)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthContextResolver.class);
                    assertThat(context.getBean(AuthContextResolver.class)).isSameAs(customResolver);
                });
    }
}
