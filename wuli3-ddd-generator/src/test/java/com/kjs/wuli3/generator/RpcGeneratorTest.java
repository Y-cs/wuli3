package com.kjs.wuli3.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 验证 RPC 发布边界、注册中心选择与生成测试的网络隔离。
 *
 * @author GuoYang create on 2026/10/16 09:00
 */
final class RpcGeneratorTest {
    @TempDir
    private Path output;

    /** 启用 RPC 时由 app.facade 发布，并为两种持久化组合生成无网络启动测试。 */
    @ParameterizedTest
    @CsvSource({"none,none", "none,nacos", "mysql,none", "mysql,nacos"})
    void publishesFacadeFromAppFacade(final String persistence, final String registry) throws Exception {
        final Path root = new DddProjectGenerator().generate(this.options("dubbo", registry, persistence));
        assertThat(Files.readString(root.resolve("order-service-app/build.gradle.kts")))
                .contains("org.apache.dubbo:dubbo-config-spring");
        assertThat(Files.readString(root.resolve(
                        "order-service-app/src/main/java/com/example/order/app/facade/order/OrderFacadeImpl.java")))
                .contains(
                        "import org.apache.dubbo.config.annotation.DubboService;",
                        "@DubboService",
                        "@Service",
                        "implements OrderFacade");
        assertThat(root.resolve(
                        "order-service-bootstrap/src/main/java/com/example/order/config/OrderRpcConfiguration.java"))
                .doesNotExist();
        final String dependencies = Files.readString(root.resolve("order-service-bootstrap/build.gradle.kts"));
        assertThat(dependencies).contains("wuli3-dubbo-spring-boot-starter").doesNotContain("dubbo-config-spring");
        final String config =
                Files.readString(root.resolve("order-service-bootstrap/src/main/resources/application.yml"));
        assertThat(config)
                .contains(
                        "serialization: fastjson2",
                        "${DUBBO_PORT:20880}",
                        "qos-enable: false",
                        "scan:",
                        "base-packages: com.example.order.app.facade");
        if (registry.equals("nacos")) {
            assertThat(dependencies).contains("runtimeOnly(\"org.apache.dubbo:dubbo-registry-nacos\")");
            assertThat(dependencies).contains("spring-cloud-starter-alibaba-nacos-config");
            assertThat(config)
                    .contains(
                            "${DUBBO_REGISTRY_ADDRESS:nacos://127.0.0.1:8848}",
                            "config-center:",
                            "${DUBBO_CONFIG_CENTER_ADDRESS:nacos://127.0.0.1:8848}",
                            "server-addr: \"${NACOS_CONFIG_SERVER_ADDR:127.0.0.1:8848}\"",
                            "optional:nacos:${NACOS_CONFIG_DATA_ID:order-service.yml}",
                            "group=${NACOS_CONFIG_GROUP:DEFAULT_GROUP}");
        } else {
            assertThat(dependencies).doesNotContain("dubbo-registry-nacos");
            assertThat(config).contains("address: \"N/A\"");
        }
        assertThat(Files.readString(root.resolve(
                        "order-service-bootstrap/src/test/java/com/example/order/OrderServiceApplicationTest.java")))
                .contains("\"dubbo.enabled=false\"");
    }

    /** 未启用 RPC 时不生成发布注解、依赖和配置。 */
    @Test
    void omitsRpcWhenDisabled() throws Exception {
        final Path root = new DddProjectGenerator().generate(this.options("none", "none", "none"));
        assertThat(root.resolve(
                        "order-service-bootstrap/src/main/java/com/example/order/config/OrderRpcConfiguration.java"))
                .doesNotExist();
        assertThat(Files.readString(root.resolve("order-service-app/build.gradle.kts")))
                .doesNotContain("dubbo");
        assertThat(Files.readString(root.resolve(
                        "order-service-app/src/main/java/com/example/order/app/facade/order/OrderFacadeImpl.java")))
                .doesNotContain("DubboService", "org.apache.dubbo");
        assertThat(Files.readString(root.resolve("order-service-bootstrap/build.gradle.kts")))
                .doesNotContain("dubbo");
        assertThat(Files.readString(root.resolve("order-service-bootstrap/src/main/resources/application.yml")))
                .doesNotContain("dubbo:");
    }

    /** Nacos 不能在缺少 RPC 能力时独立启用。 */
    @Test
    void rejectsRegistryWithoutRpc() {
        assertThatThrownBy(() -> this.options("none", "nacos", "none"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry=nacos");
    }

    /** 创建隔离输出目录对应的生成参数。 */
    private GeneratorOptions options(final String rpc, final String registry, final String persistence) {
        return GeneratorOptions.parse(new String[] {
            "--service",
            "order-service",
            "--package",
            "com.example.order",
            "--domain",
            "order",
            "--rpc",
            rpc,
            "--registry",
            registry,
            "--persistence",
            persistence,
            "--output",
            this.output.toString()
        });
    }
}
