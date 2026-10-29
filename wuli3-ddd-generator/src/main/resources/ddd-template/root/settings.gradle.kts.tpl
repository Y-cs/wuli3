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
    "{{modulePrefix}}-shared-kernel",
    "{{modulePrefix}}-domain",
    "{{modulePrefix}}-api",
    "{{modulePrefix}}-app",
    "{{modulePrefix}}-infra",
    "{{modulePrefix}}-adapter",
    "{{modulePrefix}}-bootstrap",
)
