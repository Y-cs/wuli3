package com.kjs.wuli3.spring.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class ApplicationStartupListenerTest {

    private final ApplicationStartupListener listener = new ApplicationStartupListener();

    @Test
    void resolvesActualBoundPortFromWebServer() {
        final WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        final WebServer webServer = mock(WebServer.class);
        when(context.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(8080);

        assertThat(this.listener.resolvePort(context)).isEqualTo(8080);
    }

    @Test
    void returnsMinusOneForNonWebApplication() {
        assertThat(this.listener.resolvePort(mock(ApplicationContext.class))).isEqualTo(-1);
    }

    @Test
    void printsBannerWhenApplicationIsReady() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "order-service");
        environment.setActiveProfiles("prod");
        final WebServer webServer = mock(WebServer.class);
        when(webServer.getPort()).thenReturn(8080);
        final ConfigurableApplicationContext context = mock(
                ConfigurableApplicationContext.class,
                withSettings().extraInterfaces(WebServerApplicationContext.class));
        when(context.getEnvironment()).thenReturn(environment);
        when(((WebServerApplicationContext) context).getWebServer()).thenReturn(webServer);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            this.listener.onApplicationEvent(
                    new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
        } finally {
            System.setOut(original);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("\\_/\\_/")
                .contains("启动成功")
                .contains("name: order-service")
                .contains("webPort: 8080")
                .contains("profiles: prod");
    }

    @Test
    void registersListenerThroughAutoConfigurationImports() {
        final SpringApplication application = new SpringApplication(StartupTestConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBean(ApplicationStartupListener.class)).isNotNull();
        }
    }

    @SpringBootApplication
    static class StartupTestConfiguration {}
}
