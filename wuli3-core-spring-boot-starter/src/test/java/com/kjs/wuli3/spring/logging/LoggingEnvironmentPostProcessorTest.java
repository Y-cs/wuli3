package com.kjs.wuli3.spring.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class LoggingEnvironmentPostProcessorTest {

    @TempDir
    Path temporaryDirectory;

    private final LoggingEnvironmentPostProcessor processor = new LoggingEnvironmentPostProcessor();

    @Test
    void appliesDefaultsWithoutOverridingApplicationSettings() {
        final MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "orders")
                .withProperty("logging.pattern.file", "custom-file");

        this.processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.pattern.console")).contains("app=orders");
        assertThat(environment.getProperty("logging.pattern.file")).isEqualTo("custom-file");
    }

    @Test
    void mapsStructuredFormatToSpringBootProperties() {
        final MockEnvironment environment = new MockEnvironment().withProperty("wuli3.logging.format", "ecs");

        this.processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
        assertThat(environment.getProperty("logging.pattern.console")).isNull();
    }

    @Test
    void createsParentDirectoryForFileHandler() throws IOException {
        final Path logFile = this.temporaryDirectory.resolve("logs/example.log");
        final MockEnvironment environment = new MockEnvironment()
                .withProperty("wuli3.logging.file.enabled", "true")
                .withProperty("logging.file.name", logFile.toString());

        this.processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(Files.isDirectory(this.temporaryDirectory.resolve("logs"))).isTrue();
        final FileHandler handler = new FileHandler(logFile.toString());
        try {
            assertThat(Files.isRegularFile(logFile)).isTrue();
        } finally {
            handler.close();
        }
    }

    @Test
    void createsConfiguredLogPathForFileHandler() {
        final Path logDirectory = this.temporaryDirectory.resolve("other-logs");
        final MockEnvironment environment =
                new MockEnvironment().withProperty("logging.file.path", logDirectory.toString());

        this.processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(Files.isDirectory(logDirectory)).isTrue();
    }
}
