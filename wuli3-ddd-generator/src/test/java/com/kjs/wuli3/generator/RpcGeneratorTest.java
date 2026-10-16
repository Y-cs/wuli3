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

    /** 启用 RPC 不污染应用模块，并为两种持久化组合生成无网络启动测试。 */
    @ParameterizedTest
    @CsvSource({"none,none", "none,nacos", "mysql,none", "mysql,nacos"})
    void publishesFacadeFromBootstrap(final String persistence, final String registry) throws Exception {
        final Path root = new DddProjectGenerator().generate(this.options("dubbo", registry, persistence));
        assertThat(Files.readString(root.resolve("app/build.gradle.kts"))).doesNotContain("dubbo");
        assertThat(Files.readString(
                        root.resolve("app/src/main/java/com/example/order/app/facade/order/OrderFacadeImpl.java")))
                .doesNotContain("DubboService", "org.apache.dubbo");
        assertThat(Files.readString(
                        root.resolve("bootstrap/src/main/java/com/example/order/config/OrderRpcConfiguration.java")))
                .contains(
                        "new ServiceBean<>(context)",
                        "service.setInterface(OrderFacade.class)",
                        "service.setRef(facade)",
                        "@ConditionalOnProperty(prefix = \"dubbo\", name = \"enabled\"");
        final String dependencies = Files.readString(root.resolve("bootstrap/build.gradle.kts"));
        assertThat(dependencies).contains("wuli3-dubbo-spring-boot-starter");
        final String config = Files.readString(root.resolve("bootstrap/src/main/resources/application.yml"));
        assertThat(config).contains("serialization: fastjson2", "${DUBBO_PORT:20880}");
        if (registry.equals("nacos")) {
            assertThat(dependencies).contains("runtimeOnly(\"org.apache.dubbo:dubbo-registry-nacos\")");
            assertThat(config).contains("${DUBBO_REGISTRY_ADDRESS:nacos://127.0.0.1:8848}");
        } else {
            assertThat(dependencies).doesNotContain("dubbo-registry-nacos");
            assertThat(config).contains("address: \"N/A\"");
        }
        assertThat(Files.readString(
                        root.resolve("bootstrap/src/test/java/com/example/order/BootstrapApplicationTest.java")))
                .contains("\"dubbo.enabled=false\"");
    }

    /** 未启用 RPC 时不生成发布文件、依赖和配置。 */
    @Test
    void omitsRpcWhenDisabled() throws Exception {
        final Path root = new DddProjectGenerator().generate(this.options("none", "none", "none"));
        assertThat(root.resolve("bootstrap/src/main/java/com/example/order/config/OrderRpcConfiguration.java"))
                .doesNotExist();
        assertThat(Files.readString(root.resolve("bootstrap/build.gradle.kts"))).doesNotContain("dubbo");
        assertThat(Files.readString(root.resolve("bootstrap/src/main/resources/application.yml")))
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
