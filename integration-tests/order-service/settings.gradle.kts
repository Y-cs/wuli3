pluginManagement {
    includeBuild("../../build-logic")
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("../../build/temporary-maven-repository") } }
            filter { includeGroup("com.kjs.wuli3") }
        }
        mavenCentral()
    }
}
rootProject.name = "order-service-integration-tests"
include("order-it-shared-kernel", "order-it-domain", "order-it-api", "order-it-app",
        "order-it-infra", "order-it-adapter", "order-it-bootstrap", "order-it-acceptance")
