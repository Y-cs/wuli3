package com.kjs.wuli3.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 验证 DDD 生成器的模块边界、选项渲染和写入保护。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
final class DddGeneratorTest {
    @TempDir
    private Path output;

    /** 验证默认生成完整的七模块工程和架构门禁。 */
    @Test
    void generatesModularMonolithWithArchitectureRules() throws Exception {
        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "order-service",
            "--package",
            "com.example.order",
            "--domain",
            "order",
            "--wuli3-version",
            "1.2.3",
            "--build-logic-version",
            "4.5.6",
            "--output",
            this.output.toString()
        });

        final Path root = this.output.resolve("order-service");
        assertThat(root.resolve("logs/.gitkeep")).isRegularFile();
        assertThat(Files.readString(root.resolve("lombok.config")))
                .isEqualTo("# 全局设置@EqualsAndHashCode的callSuper属性为true\n"
                        + "lombok.equalsAndHashCode.callSuper = CALL\n"
                        + "# 全局设置@ToString的callSuper属性为true\n"
                        + "lombok.toString.callSuper = CALL\n"
                        + "# 链式编程\n"
                        + "lombok.accessors.chain=TRUE");
        assertThat(Files.readString(root.resolve("AGENTS.md")))
                .contains(
                        "# order-service 项目协作规范",
                        "`com.example.order`",
                        "初始领域为 `order`",
                        "## 项目结构与模块边界",
                        "## 构建风格",
                        "## 编码风格",
                        "## 测试指南",
                        "## 提交与 Pull Request 规范",
                        "## Git 使用",
                        "## 注释使用说明",
                        "./gradlew clean check",
                        "git add <path>",
                        "yyyy/M/d HH:mm")
                .doesNotContain("{{", "/Users/", "OMX", "com.kjs.wuli3.generator", "kotlin", "Kotlin");
        assertThat(Files.readString(root.resolve("CLAUDE.md")))
                .isEqualTo("请阅读并遵循根目录 [AGENTS.md](AGENTS.md) 中的全部项目协作规范。\n");
        assertThat(root.resolve("gradlew")).isExecutable();
        assertThat(Files.readString(root.resolve("settings.gradle.kts")))
                .contains(
                        "\"shared-kernel\"",
                        "\"domain\"",
                        "\"api\"",
                        "\"app\"",
                        "\"infra\"",
                        "\"adapter\"",
                        "\"bootstrap\"");
        assertThat(Files.readString(root.resolve("shared-kernel/build.gradle.kts")))
                .contains("wuli3-core")
                .doesNotContain(
                        "spring",
                        "project(\"",
                        "`java-library`",
                        "kotlin",
                        "tasks.register",
                        "allowedSharedKernelDependencies");
        assertThat(Files.readString(root.resolve("shared-kernel/build.gradle.kts")))
                .doesNotContain("ddd-conventions");
        assertThat(Files.readString(root.resolve("build.gradle.kts"))).contains("ddd-conventions");
        assertThat(root.resolve("shared-kernel/src/test/java/com/example/order/sharedkernel/SharedKernelTest.java"))
                .exists();
        assertThat(root.resolve(
                        "shared-kernel/src/test/java/com/example/order/architecture/SharedKernelArchitectureTest.java"))
                .doesNotExist();
        assertThat(Files.readString(root.resolve("build.gradle.kts")))
                .contains(
                        "java-conventions\") version \"4.5.6\"",
                        "com.kjs.wuli3.spring-conventions",
                        "com.kjs.wuli3.java-conventions",
                        "core.hooksPath",
                        "\".githooks\"")
                .doesNotContain("JavaPluginExtension", "JavaLanguageVersion", "tasks.withType<JavaCompile>", "kotlin");
        assertThat(Files.readString(root.resolve("gradle.properties")))
                .contains(
                        "wuli3.version=1.2.3",
                        "wuli3.conventions.bom-coordinates=com.kjs.wuli3:wuli3-dependencies:1.2.3",
                        "wuli3.conventions.jacoco.verification.enabled=false");
        assertThat(Files.readString(root.resolve("README.md")))
                .contains("src/main/java", "src/test/java", "java-conventions", "spring-conventions")
                .doesNotContain("kotlin", "Kotlin");
        assertThat(Files.readString(root.resolve("domain/build.gradle.kts")))
                .contains("api(project(\":shared-kernel\"))")
                .contains("implementation(\"com.kjs.wuli3:wuli3-core\")")
                .doesNotContain("plugins", "java-library");
        assertThat(Files.readString(root.resolve("infra/build.gradle.kts")))
                .contains("project(\":app\")", "project(\":domain\")")
                .doesNotContain("wuli3-mysql");
        assertThat(root.resolve("infra/src/test/java/com/example/order/architecture/InfraArchitectureTest.java"))
                .doesNotExist();
        assertThat(Files.readString(
                        root.resolve("app/src/main/java/com/example/order/app/service/order/OrderService.java")))
                .contains(
                        "public class OrderService",
                        "import org.springframework.transaction.annotation.Transactional;",
                        "@Transactional\n    public OrderStatusDto save",
                        "@Transactional\n    public void deleteById");
        assertThat(Files.readString(root.resolve(".wuli3/generation.properties")))
                .contains("template.version=2.0.0", "persistence=none", "messaging=none", "sort.fields=id,status");
    }

    /** 验证自定义排序白名单会进入生成配置和 MySQL 查询实现。 */
    @Test
    void supportsCustomSortFields() throws Exception {
        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "catalog-service",
            "--package",
            "com.example.catalog",
            "--domain",
            "catalog",
            "--persistence",
            "mysql",
            "--sort-fields",
            "id,created_at",
            "--output",
            this.output.toString()
        });
        final Path root = this.output.resolve("catalog-service");
        assertThat(Files.readString(root.resolve(".wuli3/generation.properties")))
                .contains("sort.fields=id,created_at");
        final Path queryRepository = root.resolve(
                "infra/src/main/java/com/example/catalog/infra/persistence/catalog/CatalogQueryRepositoryImpl.java");
        assertThat(Files.readString(queryRepository)).contains("Set.of(\"id\",\"created_at\")");
    }

    /** 验证基础设施选项只修改 Infra 组合并生成对应持久化类型。 */
    @Test
    void generatesSelectedInfrastructureAdapters() throws Exception {
        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "catalog-service",
            "--package",
            "com.example.catalog",
            "--domain",
            "product-catalog",
            "--persistence",
            "mysql",
            "--messaging",
            "rocketmq",
            "--output",
            this.output.toString()
        });

        final Path root = this.output.resolve("catalog-service");
        assertThat(Files.readString(root.resolve("infra/build.gradle.kts")))
                .contains("wuli3-mysql-spring-boot-starter", "wuli3-rocketmq-spring-boot-starter");
        assertThat(Files.readString(root.resolve("README.md"))).contains("消息选项为 `rocketmq`", "不生成具体 Listener");
        assertThat(root.resolve("infra/src/main/java/com/example/catalog/infra/persistence/product_catalog/"
                        + "mapper/ProductCatalogMapper.java"))
                .exists();
        assertThat(root.resolve("domain/src/main/java/com/example/catalog/domain/product_catalog/ProductCatalog.java"))
                .exists();
        assertThat(Files.readString(
                        root.resolve("infra/src/main/java/com/example/catalog/infra/persistence/product_catalog/"
                                + "ProductCatalogRepositoryImpl.java")))
                .contains(
                        "public class ProductCatalogRepositoryImpl",
                        "private final ProductCatalogMapper mapper",
                        "this.mapper.selectById")
                .doesNotContain("public final class ProductCatalogRepositoryImpl");
        assertThat(Files.readString(root.resolve("infra/src/main/java/com/example/catalog/infra/config/"
                        + "ProductCatalogPersistenceConfiguration.java")))
                .contains(
                        "@MapperScan(basePackages = \"com.example.catalog.infra.persistence.**\"",
                        "@Configuration(proxyBeanMethods = false)",
                        "PaginationInnerInterceptor");
        assertThat(Files.readString(
                        root.resolve("infra/src/main/java/com/example/catalog/infra/persistence/product_catalog/"
                                + "ProductCatalogQueryRepositoryImpl.java")))
                .contains(
                        "implements ProductCatalogQueryRepository",
                        "this.mapper.selectById",
                        "PaginationHelper.toPageResult")
                .doesNotContain("domain.product_catalog", "ProductCatalogRepository");
        assertThat(Files.readString(root.resolve("bootstrap/src/main/resources/application.yml")))
                .contains("name: catalog-service", "service-code: catalog-service")
                .doesNotContain("generator:", "messaging:");
        assertThat(Files.readString(
                        root.resolve("bootstrap/src/test/java/com/example/catalog/BootstrapApplicationTest.java")))
                .contains(
                        "import com.example.catalog.infra.persistence.product_catalog.mapper.ProductCatalogMapper;",
                        "@MockitoBean(types = ProductCatalogMapper.class)",
                        "application.persistence.enabled=false",
                        "@ImportAutoConfiguration(",
                        "exclude = {",
                        "DataSourceAutoConfiguration.class",
                        "JdbcTemplateAutoConfiguration.class");
    }

    /** 验证所有基础设施组合都保留包契约，且旧端口结构与模板占位符不再泄漏。 */
    @ParameterizedTest
    @CsvSource({"none, none", "none, rocketmq", "none, rabbitmq", "mysql, none", "mysql, rocketmq", "mysql, rabbitmq"})
    void generatesDocumentedPackagesForEveryInfrastructureCombination(final String persistence, final String messaging)
            throws Exception {
        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "order-service",
            "--package",
            "com.example.order",
            "--domain",
            "order",
            "--persistence",
            persistence,
            "--messaging",
            messaging,
            "--output",
            this.output.toString()
        });
        final Path root = this.output.resolve("order-service");
        assertThat(root.resolve("app/src/main/java/com/example/order/app/acl")).doesNotExist();
        for (final String packagePath : List.of(
                "api/facade",
                "api/dto/order",
                "app/facade/order",
                "app/service/order",
                "app/repository/order",
                "domain/gateway",
                "adapter/api/order",
                "adapter/mq",
                "adapter/job",
                "domain/order",
                "infra/config",
                "infra/acl",
                "infra/persistence/order",
                "sharedkernel")) {
            final int separator = packagePath.indexOf('/');
            final String module =
                    packagePath.equals("sharedkernel") ? "shared-kernel" : packagePath.substring(0, separator);
            final Path description =
                    root.resolve(module + "/src/main/java/com/example/order/" + packagePath + "/package-info.java");
            assertThat(description).isRegularFile();
            assertThat(Files.readString(description))
                    .contains("/**", "@author", "package com.example.order." + packagePath.replace('/', '.') + ";")
                    .doesNotContain("{{");
        }
        assertThat(root.resolve("app/src/main/java/com/example/order/app/port")).doesNotExist();
        assertThat(root.resolve("app/src/main/java/com/example/order/app/internal"))
                .doesNotExist();
        assertThat(root.resolve("adapter/src/main/java/com/example/order/adapter/in"))
                .doesNotExist();
        assertThat(root.resolve("api/src/main/java/com/example/order/api/facade/OrderFacade.java"))
                .isRegularFile();
        assertThat(root.resolve("app/src/main/java/com/example/order/app/facade/order/OrderFacadeImpl.java"))
                .isRegularFile();
        assertThat(Files.readString(
                        root.resolve("app/src/main/java/com/example/order/app/facade/order/OrderFacadeImpl.java")))
                .contains(
                        "implements OrderFacade",
                        "this.service.findById",
                        "this.service.save",
                        "this.service.deleteById")
                .doesNotContain("QueryRepository", "Request", "Response");
        assertThat(Files.readString(
                        root.resolve("adapter/src/main/java/com/example/order/adapter/api/order/OrderApi.java")))
                .contains("OrderService", "this.service.findById", "this.service.save", "this.service.deleteById")
                .doesNotContain("OrderFacade", "QueryRepository");
        assertThat(root.resolve("domain/src/main/java/com/example/order/domain/order/OrderRepository.java"))
                .isRegularFile();
        assertThat(root.resolve("adapter/src/main/java/com/example/order/adapter/api/order/OrderApi.java"))
                .isRegularFile();
        try (final var paths = Files.walk(root)) {
            for (final Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> !file.startsWith(root.resolve(".git")))
                    .filter(file -> !file.toString().endsWith(".jar"))
                    .toList()) {
                assertThat(Files.readString(path))
                        .as("rendered file %s", root.relativize(path))
                        .doesNotContain("{{", "}}");
                if (path.toString().contains("/src/main/java/")
                        && path.toString().endsWith(".java")) {
                    assertThat(path.resolveSibling("package-info.java"))
                            .as("package contract for %s", root.relativize(path))
                            .isRegularFile();
                }
            }
        }
        final String infraBuild = Files.readString(root.resolve("infra/build.gradle.kts"));
        assertThat(infraBuild.contains("wuli3-mysql-spring-boot-starter")).isEqualTo(persistence.equals("mysql"));
        assertThat(infraBuild.contains("wuli3-rocketmq-spring-boot-starter")).isEqualTo(messaging.equals("rocketmq"));
        assertThat(infraBuild.contains("wuli3-rabbitmq-spring-boot-starter")).isEqualTo(messaging.equals("rabbitmq"));
    }

    /** 验证关键字和字面量不能用于包名，且拒绝输入时不创建工程。 */
    @ParameterizedTest
    @CsvSource({
        "com.example.class, order",
        "class.example, order",
        "com.true.example, order",
        "com.example.null, order",
        "com.example.order, class",
        "com.example.order, int",
        "com.example.order, true",
        "com.example.order, false",
        "com.example.order, null"
    })
    void rejectsInvalidJavaNamesBeforeWriting(final String basePackage, final String domain) {
        assertThatThrownBy(() -> DddGenerator.main(new String[] {
                    "generate",
                    "--service",
                    "order-service",
                    "--package",
                    basePackage,
                    "--domain",
                    domain,
                    "--output",
                    this.output.toString()
                }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(this.output.resolve("order-service")).doesNotExist();
    }

    /** 验证合法的上下文关键字包段和带连字符的领域名仍可使用。 */
    @ParameterizedTest
    @CsvSource({"com.example.record, record", "com.example.module, product-catalog", "com.example.var, class-order"})
    void acceptsLegalJavaPackageNames(final String basePackage, final String domain) {
        final GeneratorOptions options = GeneratorOptions.parse(
                new String[] {"--service", "order-service", "--package", basePackage, "--domain", domain});
        assertThat(options.basePackage()).isEqualTo(basePackage);
        assertThat(options.domain()).isEqualTo(domain);
    }

    /** 验证必填参数缺失时给出明确错误。 */
    @Test
    void rejectsMissingRequiredArguments() {
        assertThatThrownBy(() -> DddGenerator.main(new String[] {"generate", "--service", "order-service"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 --package");
    }

    /** 验证生成器不会覆盖非空目标目录。 */
    @Test
    void rejectsNonEmptyTargetDirectory() throws Exception {
        final Path root = Files.createDirectories(this.output.resolve("order-service"));
        Files.writeString(root.resolve("owned-by-user.txt"), "keep");

        assertThatThrownBy(() -> DddGenerator.main(new String[] {
                    "generate",
                    "--service",
                    "order-service",
                    "--package",
                    "com.example.order",
                    "--domain",
                    "order",
                    "--output",
                    this.output.toString()
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标目录非空");
        assertThat(root.resolve("owned-by-user.txt")).hasContent("keep");
    }

    /** 验证生成结果已初始化为 git 仓库，钩子落盘可执行且 core.hooksPath 指向版本化目录。 */
    @Test
    void initializesGitRepositoryWithVersionedHooks() throws Exception {
        assumeTrue(DddGeneratorTest.gitAvailable(), "本机无 git，跳过仓库与钩子断言");

        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "order-service",
            "--package",
            "com.example.order",
            "--domain",
            "order",
            "--output",
            this.output.toString()
        });

        final Path root = this.output.resolve("order-service");
        assertThat(root.resolve(".git")).isDirectory();
        assertThat(root.resolve(".githooks/pre-commit")).isExecutable();
        assertThat(DddGeneratorTest.git(root, "config", "--get", "core.hooksPath")
                        .output())
                .isEqualTo(".githooks");
        assertThat(Files.readString(root.resolve(".githooks/pre-commit")))
                .contains(
                        "./gradlew spotlessApply",
                        "./gradlew forbiddenApis",
                        "git add --",
                        "core.hooksPath",
                        "--no-verify")
                .doesNotContain("{{");
    }

    /** 验证祖先目录已是 git 仓库时不创建嵌套仓库，也不改写其 core.hooksPath。 */
    @Test
    void skipsGitInitializationInsideExistingRepository() throws Exception {
        assumeTrue(DddGeneratorTest.gitAvailable(), "本机无 git，跳过仓库与钩子断言");

        final Path outer = Files.createDirectories(this.output.resolve("outer"));
        assertThat(DddGeneratorTest.git(outer, "init").exitCode()).isZero();

        DddGenerator.main(new String[] {
            "generate",
            "--service",
            "order-service",
            "--package",
            "com.example.order",
            "--domain",
            "order",
            "--output",
            outer.toString()
        });

        final Path root = outer.resolve("order-service");
        assertThat(root.resolve(".git")).doesNotExist();
        assertThat(root.resolve(".githooks/pre-commit")).isExecutable();
        assertThat(DddGeneratorTest.git(outer, "config", "--get", "core.hooksPath")
                        .output())
                .isEmpty();
    }

    /** git 是否可用；缺失时相关断言跳过而非失败。 */
    private static boolean gitAvailable() {
        try {
            return new ProcessBuilder("git", "--version")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    == 0;
        } catch (final IOException exception) {
            return false;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 在指定目录执行 git，返回退出码与合并后的标准输出。 */
    private static GitResult git(final Path directory, final String... arguments) throws Exception {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        return new GitResult(process.waitFor(), output);
    }

    private record GitResult(int exitCode, String output) {}
}
