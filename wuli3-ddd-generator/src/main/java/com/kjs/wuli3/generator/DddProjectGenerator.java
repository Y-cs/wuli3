package com.kjs.wuli3.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 使用内置版本化模板生成单体多模块 DDD 工程。
 *
 * 注意：生成器拒绝写入非空目录，避免覆盖业务代码。
 *
 * @author GuoYang create on 2026/9/7 15:30
 */
final class DddProjectGenerator {
    private static final String TEMPLATE_ROOT = "/ddd-template/";
    private static final String TEMPLATE_VERSION = "2.0.0";

    /** 模板文件与生成路径的映射，按生成工程的模块顺序排列。 */
    private static final List<TemplateFile> BASE_FILES = List.of(
            // root
            file("root/settings.gradle.kts.tpl", "settings.gradle.kts"),
            file("root/build.gradle.kts.tpl", "build.gradle.kts"),
            file("root/gradle.properties.tpl", "gradle.properties"),
            file("root/lombok.config.tpl", "lombok.config"),
            file("root/README.md.tpl", "README.md"),
            file("root/AGENTS.md.tpl", "AGENTS.md"),
            file("root/Dockerfile.tpl", "Dockerfile"),
            file("root/dockerignore.tpl", ".dockerignore"),
            file("root/gitignore.tpl", ".gitignore"),
            file("root/githooks/pre-commit.tpl", ".githooks/pre-commit"),
            file("root/generation.properties.tpl", ".wuli3/generation.properties"),
            // shared-kernel
            file("shared-kernel/shared-kernel.gradle.kts.tpl", "shared-kernel/build.gradle.kts"),
            file(
                    "shared-kernel/Status.java.tpl",
                    "shared-kernel/src/main/java/{{packagePath}}/sharedkernel/Status.java"),
            file(
                    "shared-kernel/EntityId.java.tpl",
                    "shared-kernel/src/main/java/{{packagePath}}/sharedkernel/EntityId.java"),
            file(
                    "shared-kernel/SharedKernelTest.java.tpl",
                    "shared-kernel/src/test/java/{{packagePath}}/sharedkernel/SharedKernelTest.java"),
            // domain
            file("domain/domain.gradle.kts.tpl", "domain/build.gradle.kts"),
            file(
                    "domain/Domain.java.tpl",
                    "domain/src/main/java/{{packagePath}}/domain/{{domainPackage}}/{{domainType}}.java"),
            file(
                    "domain/DomainTest.java.tpl",
                    "domain/src/test/java/{{packagePath}}/domain/{{domainPackage}}/{{domainType}}Test.java"),
            // api
            file("api/api.gradle.kts.tpl", "api/build.gradle.kts"),
            file("api/DomainFacade.java.tpl", "api/src/main/java/{{packagePath}}/api/facade/{{domainType}}Facade.java"),
            file(
                    "api/DomainStatusDto.java.tpl",
                    "api/src/main/java/{{packagePath}}/api/dto/{{domainPackage}}/{{domainType}}StatusDto.java"),
            // app
            file("app/app.gradle.kts.tpl", "app/build.gradle.kts"),
            file(
                    "app/DomainFacadeImpl.java.tpl",
                    "app/src/main/java/{{packagePath}}/app/facade/{{domainPackage}}/{{domainType}}FacadeImpl.java"),
            file(
                    "app/DomainQueryService.java.tpl",
                    "app/src/main/java/{{packagePath}}/app/service/{{domainPackage}}/{{domainType}}QueryService.java"),
            file(
                    "app/DomainQueryRepository.java.tpl",
                    "app/src/main/java/{{packagePath}}/app/repository/{{domainPackage}}/{{domainType}}QueryRepository.java"),
            file(
                    "domain/DomainRepository.java.tpl",
                    "domain/src/main/java/{{packagePath}}/domain/{{domainPackage}}/{{domainType}}Repository.java"),
            // infra
            file("infra/infra.gradle.kts.tpl", "infra/build.gradle.kts"),
            file(
                    "infra/DomainRepositoryImpl.java.tpl",
                    "infra/src/main/java/{{packagePath}}/infra/persistence/{{domainPackage}}/{{domainType}}RepositoryImpl.java"),
            file(
                    "infra/DomainQueryRepositoryImpl.java.tpl",
                    "infra/src/main/java/{{packagePath}}/infra/persistence/{{domainPackage}}/{{domainType}}QueryRepositoryImpl.java"),
            // adapter
            file("adapter/adapter.gradle.kts.tpl", "adapter/build.gradle.kts"),
            file(
                    "adapter/DomainApi.java.tpl",
                    "adapter/src/main/java/{{packagePath}}/adapter/api/{{domainPackage}}/{{domainType}}Api.java"),
            file(
                    "app/DomainFacadeImplTest.java.tpl",
                    "app/src/test/java/{{packagePath}}/app/facade/{{domainPackage}}/{{domainType}}FacadeImplTest.java"),
            file(
                    "adapter/DomainApiTest.java.tpl",
                    "adapter/src/test/java/{{packagePath}}/adapter/api/{{domainPackage}}/{{domainType}}ApiTest.java"),
            // bootstrap
            file("bootstrap/bootstrap.gradle.kts.tpl", "bootstrap/build.gradle.kts"),
            file(
                    "bootstrap/BootstrapApplication.java.tpl",
                    "bootstrap/src/main/java/{{packagePath}}/BootstrapApplication.java"),
            file("bootstrap/application.yml.tpl", "bootstrap/src/main/resources/application.yml"),
            file(
                    "bootstrap/BootstrapApplicationTest.java.tpl",
                    "bootstrap/src/test/java/{{packagePath}}/BootstrapApplicationTest.java"));

    /** 选择 MySQL 持久化时额外生成的 infra 文件。 */
    private static final List<TemplateFile> MYSQL_FILES = List.of(
            file(
                    "infra/mysql/DomainPo.java.tpl",
                    "infra/src/main/java/{{packagePath}}/infra/persistence/{{domainPackage}}/entity/{{domainType}}Po.java"),
            file(
                    "infra/mysql/DomainMapper.java.tpl",
                    "infra/src/main/java/{{packagePath}}/infra/persistence/{{domainPackage}}/mapper/{{domainType}}Mapper.java"),
            file(
                    "infra/mysql/DomainPersistenceConfiguration.java.tpl",
                    "infra/src/main/java/{{packagePath}}/infra/config/{{domainType}}PersistenceConfiguration.java"));

    private static final Map<String, String> WRAPPER_FILES = Map.of(
            "wrapper/gradlew", "gradlew",
            "wrapper/gradlew.bat", "gradlew.bat",
            "wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.jar",
            "wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.properties");

    /** 生成后需要可执行位的脚本，相对生成根目录。 */
    private static final List<String> EXECUTABLE_FILES = List.of("gradlew", ".githooks/pre-commit");

    /** 版本化钩子目录，通过 core.hooksPath 生效，避免写入不参与版本管理的 .git/hooks。 */
    private static final String GIT_HOOKS_PATH = ".githooks";

    /** 生成工程并返回工程根目录。 */
    Path generate(final GeneratorOptions options) throws IOException {
        final Path root = options.output().resolve(options.service()).normalize();
        this.requireWritableTarget(root);
        Files.createDirectories(root);
        final Map<String, String> variables = this.variables(options);

        final List<TemplateFile> files = new ArrayList<>(DddProjectGenerator.BASE_FILES);
        if (options.persistence().equals("mysql")) {
            files.addAll(DddProjectGenerator.MYSQL_FILES);
        }
        for (final TemplateFile template : files) {
            this.render(root, template, variables);
        }
        this.renderPackageDocumentation(root, options, variables);
        for (final Map.Entry<String, String> wrapper : DddProjectGenerator.WRAPPER_FILES.entrySet()) {
            this.copyResource(root, wrapper.getKey(), wrapper.getValue());
        }
        for (final String script : DddProjectGenerator.EXECUTABLE_FILES) {
            this.makeExecutable(root.resolve(script));
        }
        this.initGitRepository(root);
        return root;
    }

    /**
     * 在生成根目录初始化 git 仓库并启用版本化钩子目录。
     *
     * 注意：祖先目录已是 git 仓库时不做任何写入，避免嵌套仓库或改写父仓库的 core.hooksPath；
     * git 不可用或命令失败只提示，不影响已生成的工程内容。
     */
    private void initGitRepository(final Path root) {
        if (this.hasAncestorRepository(root)) {
            System.out.println("祖先目录已存在 git 仓库，跳过 git init 与钩子配置：" + root);
            return;
        }
        if (this.runGit(root, "init")
                && this.runGit(root, "config", "core.hooksPath", DddProjectGenerator.GIT_HOOKS_PATH)) {
            return;
        }
        System.out.println(
                "git 不可用或执行失败，请在 " + root + " 手动执行 git config core.hooksPath " + DddProjectGenerator.GIT_HOOKS_PATH);
    }

    /** 判断任一祖先目录是否已经是 git 仓库。 */
    private boolean hasAncestorRepository(final Path root) {
        for (Path current = root.toAbsolutePath().getParent(); current != null; current = current.getParent()) {
            if (Files.isDirectory(current.resolve(".git"))) {
                return true;
            }
        }
        return false;
    }

    /** 在指定目录执行 git 子命令，失败时打印命令输出并返回 false。 */
    private boolean runGit(final Path directory, final String... arguments) {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                System.out.println("git " + String.join(" ", arguments) + " 失败：" + output.strip());
                return false;
            }
            return true;
        } catch (final IOException exception) {
            System.out.println("无法执行 git：" + exception.getMessage());
            return false;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.out.println("git 执行被中断");
            return false;
        }
    }

    private void requireWritableTarget(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("目标路径不是目录: " + root);
        }
        try (var entries = Files.list(root)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalArgumentException("目标目录非空: " + root);
            }
        }
    }

    private Map<String, String> variables(final GeneratorOptions options) {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("service", options.service());
        result.put("basePackage", options.basePackage());
        result.put("packagePath", options.basePackage().replace('.', '/'));
        result.put("domain", options.domain());
        result.put("domainPackage", options.domain().replace('-', '_'));
        result.put("domainType", options.domainType());
        result.put("persistence", options.persistence());
        result.put("messaging", options.messaging());
        result.put("wuli3Version", options.wuli3Version());
        result.put("buildLogicVersion", options.buildLogicVersion());
        result.put("templateVersion", DddProjectGenerator.TEMPLATE_VERSION);
        result.put("infraDependencies", this.infraDependencies(options));
        result.put("repositoryImports", this.repositoryImports(options));
        result.put(
                "queryRepositoryConstructor",
                this.repositoryConstructor(options).replace("RepositoryImpl(", "QueryRepositoryImpl("));
        result.put("queryRepositoryStatus", this.queryRepositoryStatus(options));
        result.put("repositoryFields", this.repositoryFields(options));
        result.put("repositoryConstructor", this.repositoryConstructor(options));
        result.put("repositoryLoad", this.repositoryLoad(options));
        result.put("bootstrapTestImports", this.bootstrapTestImports(options));
        result.put("bootstrapTestConfiguration", this.bootstrapTestConfiguration(options));
        return result;
    }

    /** infra 构建脚本的依赖片段：持久化与消息中间件按选项切换。 */
    private String infraDependencies(final GeneratorOptions options) {
        final StringBuilder result = new StringBuilder();
        if (options.persistence().equals("mysql")) {
            result.append("    implementation(\"com.kjs.wuli3:wuli3-mysql-spring-boot-starter\")\n");
        } else {
            result.append("    implementation(\"org.springframework:spring-context\")\n");
        }
        if (options.messaging().equals("rocketmq")) {
            result.append("    implementation(\"com.kjs.wuli3:wuli3-rocketmq-spring-boot-starter\")\n");
        } else if (options.messaging().equals("rabbitmq")) {
            result.append("    implementation(\"com.kjs.wuli3:wuli3-rabbitmq-spring-boot-starter\")\n");
        }
        return result.toString();
    }

    /** MySQL 仓储显式导入所属领域的持久化类型。 */
    private String repositoryImports(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return "";
        }
        final String prefix =
                options.basePackage() + ".infra.persistence." + options.domain().replace('-', '_');
        return "import " + prefix + ".entity." + options.domainType() + "Po;\n" + "import " + prefix + ".mapper."
                + options.domainType() + "Mapper;\n";
    }

    /** 查询仓储直接构建投影，不经过聚合仓储或领域模型转换。 */
    private String queryRepositoryStatus(final GeneratorOptions options) {
        final String constructor = "new " + options.domainType() + "StatusDto";
        if (!options.persistence().equals("mysql")) {
            return "        return " + constructor + "(new EntityId(\"sample-1\"), Status.ACTIVE);";
        }
        return "        final " + options.domainType() + "Po stored = this.mapper.selectById(\"sample-1\");\n"
                + "        final String id = stored == null ? \"sample-1\" : stored.id();\n"
                + "        return " + constructor + "(new EntityId(id), Status.ACTIVE);";
    }

    /** 仓储适配器的 Mapper 字段声明，非 MySQL 时为空。 */
    private String repositoryFields(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return "";
        }
        return "    private final " + options.domainType() + "Mapper mapper;\n";
    }

    /** 仓储适配器的构造器，非 MySQL 时为空。 */
    private String repositoryConstructor(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return "";
        }
        return "\n    public " + options.domainType() + "RepositoryImpl(final " + options.domainType()
                + "Mapper mapper) {\n        this.mapper = mapper;\n    }\n";
    }

    /** 仓储适配器的加载实现，MySQL 选项下走 Mapper 查询。 */
    private String repositoryLoad(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return "        return new " + options.domainType() + "(new EntityId(\"sample-1\"));";
        }
        return "        final " + options.domainType() + "Po stored = this.mapper.selectById(\"sample-1\");\n"
                + "        if (stored == null) {\n"
                + "            return new " + options.domainType() + "(new EntityId(\"sample-1\"));\n"
                + "        }\n"
                + "        return stored.toDomain();";
    }

    /** 启动测试的导入片段，MySQL 选项下需要额外导入自动配置与 Mockito。 */
    private String bootstrapTestImports(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return """
                    import org.junit.jupiter.api.Test;
                    import org.springframework.boot.test.context.SpringBootTest;""";
        }
        return String.join(
                "\n",
                "import " + options.basePackage() + ".infra.persistence."
                        + options.domain().replace('-', '_') + ".mapper." + options.domainType() + "Mapper;",
                "import org.junit.jupiter.api.Test;",
                "import org.springframework.boot.autoconfigure.ImportAutoConfiguration;",
                "import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;",
                "import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;",
                "import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;",
                "import org.springframework.boot.test.context.SpringBootTest;",
                "import org.springframework.test.context.bean.override.mockito.MockitoBean;");
    }

    /** 启动测试的类注解片段，MySQL 选项下排除数据源自动配置并 Mock Mapper。 */
    private String bootstrapTestConfiguration(final GeneratorOptions options) {
        if (!options.persistence().equals("mysql")) {
            return "";
        }
        return "(properties = \"application.persistence.enabled=false\")\n"
                + String.join(
                        "\n",
                        "@MockitoBean(types = " + options.domainType() + "Mapper.class)",
                        "@ImportAutoConfiguration(",
                        "        exclude = {",
                        "            DataSourceAutoConfiguration.class,",
                        "            DataSourceTransactionManagerAutoConfiguration.class,",
                        "            JdbcTemplateAutoConfiguration.class",
                        "        })");
    }

    /** 为约定包生成可版本管理的中文职责与依赖边界说明。 */
    private void renderPackageDocumentation(
            final Path root, final GeneratorOptions options, final Map<String, String> variables) throws IOException {
        final Map<String, String> packages = new LinkedHashMap<>();
        packages.put("api", "内部 RPC 契约；只依赖共享内核，不依赖应用实现或 RPC 框架。");
        packages.put("api.facade", "内部调用 Facade 契约；传播对象使用 DTO，业务实现位于 app.facade。");
        packages.put("api.dto", "可传播的内部契约对象；按业务组织，不直接暴露聚合或持久化实体。");
        packages.put("adapter", "HTTP、消息与任务接入；只负责协议和身份转换，业务编排交由应用服务。");
        packages.put("adapter.api", "HTTP 接口入口；类名以 Api 结尾，复用应用用例，避免重复业务流程。");
        packages.put("adapter.mq", "消息消费者入口；处理消息协议与幂等触发，发送消息的基础设施实现不放在此包。");
        packages.put("adapter.job", "定时任务入口；负责调度参数适配，完整业务操作委托应用服务。");
        packages.put("app", "应用用例核心；组织领域行为与事务，查询和聚合写入使用独立仓储契约。");
        packages.put("app.facade", "内部 Facade 实现；委托核心 Service，不重复业务编排或绑定 RPC 发布框架。");
        packages.put("app.service", "业务用例服务；按业务领域分包，按内聚职责拆类，不强制按调用方或读写拆分。");
        packages.put("app.repository", "应用查询仓储契约；只定义查询投影能力，聚合加载与保存契约归 domain。");
        packages.put("app.acl", "外部业务能力的防腐契约；供 infra 实现的接口以 Acl 结尾，使用本地语义，禁止泄漏外部 SDK、RPC DTO 和协议类型。");
        packages.put("domain", "领域模型；按业务概念和聚合组织，领域规则不依赖应用层或基础设施。");
        packages.put("infra", "基础设施实现；实现领域仓储、查询仓储与 ACL，不反向依赖应用 Service 或 Facade。");
        packages.put("infra.config", "基础设施技术配置；显式限定扫描范围，运行凭据通过外部环境提供。");
        packages.put("infra.persistence", "持久化实现；分别实现查询投影与聚合仓储，按业务领域组织。");
        packages.put("infra.acl", "外部能力防腐实现；转换外部模型、状态和错误，实现 app.acl 中以 Acl 结尾的契约。");
        packages.put("sharedkernel", "稳定共享类型与约定；保持无框架依赖，由构建插件检查纯净边界。");
        final String domain = options.domain().replace('-', '_');
        for (final String parent : List.of(
                "api.dto",
                "adapter.api",
                "app.facade",
                "app.service",
                "app.repository",
                "domain",
                "infra.persistence")) {
            packages.put(parent + "." + domain, options.domainType() + " 业务包。" + packages.get(parent));
        }
        if (options.persistence().equals("mysql")) {
            packages.put("infra.persistence." + domain + ".mapper", "当前领域的数据库 Mapper；只由基础设施调用，扫描配置位于 infra.config。");
            packages.put("infra.persistence." + domain + ".entity", "当前领域的持久化映射对象；不得直接作为 HTTP 或 RPC 传播对象。");
        }
        final Map<String, String> bootstrapVariables = new LinkedHashMap<>(variables);
        bootstrapVariables.put("packageName", options.basePackage());
        bootstrapVariables.put("packageDescription", "应用启动与最终组装；负责启用基础设施和接入配置，不承载业务规则。");
        this.render(
                root,
                DddProjectGenerator.file(
                        "package-info.java.tpl", "bootstrap/src/main/java/{{packagePath}}/package-info.java"),
                bootstrapVariables);
        for (final Map.Entry<String, String> entry : packages.entrySet()) {
            final String packageName = entry.getKey();
            final String module = packageName.equals("sharedkernel")
                    ? "shared-kernel"
                    : packageName.substring(
                            0, packageName.contains(".") ? packageName.indexOf('.') : packageName.length());
            final Map<String, String> packageVariables = new LinkedHashMap<>(variables);
            packageVariables.put("packageName", options.basePackage() + "." + packageName);
            packageVariables.put("packageDescription", entry.getValue());
            this.render(
                    root,
                    DddProjectGenerator.file(
                            "package-info.java.tpl",
                            module + "/src/main/java/{{packagePath}}/" + packageName.replace('.', '/')
                                    + "/package-info.java"),
                    packageVariables);
        }
    }

    private void render(final Path root, final TemplateFile template, final Map<String, String> variables)
            throws IOException {
        String content = this.readText(template.resource());
        String relativePath = template.target();
        for (final Map.Entry<String, String> variable : variables.entrySet()) {
            final String token = "{{" + variable.getKey() + "}}";
            content = content.replace(token, variable.getValue());
            relativePath = relativePath.replace(token, variable.getValue());
        }
        if (content.contains("{{") || relativePath.contains("{{")) {
            throw new IllegalStateException("模板包含未解析变量: " + template.resource());
        }
        this.write(root.resolve(relativePath), content.getBytes(StandardCharsets.UTF_8));
    }

    private void copyResource(final Path root, final String resource, final String target) throws IOException {
        try (InputStream input = this.resource(resource)) {
            this.write(root.resolve(target), input.readAllBytes());
        }
    }

    private String readText(final String resource) throws IOException {
        try (InputStream input = this.resource(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream resource(final String resource) {
        return Objects.requireNonNull(
                DddProjectGenerator.class.getResourceAsStream(DddProjectGenerator.TEMPLATE_ROOT + resource),
                "缺少模板资源: " + resource);
    }

    private void write(final Path target, final byte[] content) throws IOException {
        Files.createDirectories(Objects.requireNonNull(target.getParent()));
        Files.write(target, content);
    }

    private void makeExecutable(final Path script) throws IOException {
        try {
            final Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(script));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(script, permissions);
        } catch (final UnsupportedOperationException ignored) {
            // Windows 文件系统不支持 POSIX 权限，gradlew.bat 仍可直接使用。
        }
    }

    private static TemplateFile file(final String resource, final String target) {
        return new TemplateFile(resource, target);
    }

    private record TemplateFile(String resource, String target) {}
}
