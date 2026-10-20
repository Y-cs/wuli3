pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "{{service}}"

include(
    "{{service}}-shared-kernel",
    "{{service}}-domain",
    "{{service}}-api",
    "{{service}}-app",
    "{{service}}-infra",
    "{{service}}-adapter",
    "{{service}}-bootstrap",
)
