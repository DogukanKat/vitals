rootProject.name = "vitals"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "vitals-core",
    "vitals-static-engine",
    "vitals-rules-jpa",
    "vitals-rules-spring",
    "vitals-rules-kafka",
    "vitals-rules-redis",
    "vitals-rules-jvm",
    "vitals-cli",
)
