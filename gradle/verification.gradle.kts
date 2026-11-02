import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Delete

// 仓库级发布消费验收；由根构建加载，不随业务构建约定发布。
val publishAllPublicationsToTemporaryRepository = tasks.register("publishAllPublicationsToTemporaryRepository") {
    group = "publishing"
    description = "Publishes every public component and the BOM to the temporary Maven repository."
}

gradle.projectsEvaluated {
    publishAllPublicationsToTemporaryRepository.configure {
        dependsOn(subprojects.mapNotNull { project ->
            project.tasks.findByName("publishAllPublicationsToTemporaryRepository")
        })
    }
}

val verifyGradleBomConsumer = tasks.register<Exec>("verifyGradleBomConsumer") {
    group = "verification"
    dependsOn(publishAllPublicationsToTemporaryRepository)
    workingDir(layout.projectDirectory.dir("integration-tests/gradle-consumer"))
    commandLine(
        "../../gradlew",
        "--no-daemon",
        "--gradle-user-home",
        "../../build/consumer-gradle-home",
        "--project-cache-dir",
        "../../build/consumer-gradle-project-cache",
        "clean",
        "test",
    )
}

val cleanMavenConsumerRepository = tasks.register<Delete>("cleanMavenConsumerRepository") {
    delete(layout.buildDirectory.dir("consumer-maven-home"))
}

val verifyMavenBomConsumer = tasks.register<Exec>("verifyMavenBomConsumer") {
    group = "verification"
    dependsOn(publishAllPublicationsToTemporaryRepository, cleanMavenConsumerRepository)
    workingDir(layout.projectDirectory.dir("integration-tests/maven-consumer"))
    // 解析为绝对路径，避免守护进程启动时的 PATH 与当前构建环境不同。
    val mavenCommand = if (System.getProperty("os.name").startsWith("Windows")) "mvn.cmd" else "mvn"
    val mavenExecutable = providers.gradleProperty("wuli3.maven.executable")
        .orElse(providers.environmentVariable("MAVEN_HOME").map { "$it/bin/$mavenCommand" })
        .orElse(providers.environmentVariable("M2_HOME").map { "$it/bin/$mavenCommand" })
        .orElse(providers.environmentVariable("PATH").map { path ->
            path.split(File.pathSeparator)
                .map { File(it, mavenCommand) }
                .firstOrNull { it.isFile && it.canExecute() }
                ?.absolutePath
                ?: throw GradleException(
                    "Maven executable not found. Set -Pwuli3.maven.executable=/absolute/path/to/$mavenCommand " +
                        "or MAVEN_HOME, or add Maven to PATH.",
                )
        })
    commandLine(
        file(mavenExecutable.get()).absolutePath,
        "--settings",
        "settings.xml",
        "-Dmaven.repo.local=../../build/consumer-maven-home",
        "test",
    )
}

tasks.register("verifyBomConsumers") {
    group = "verification"
    description = "Verifies versionless Gradle and Maven consumption through the Wuli3 BOM."
    dependsOn(verifyGradleBomConsumer, verifyMavenBomConsumer)
}
