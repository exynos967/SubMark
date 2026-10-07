pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SubMark"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":feature:subscriptions")
include(":feature:money")
include(":feature:overview")
include(":feature:calendar")
include(":feature:analytics")
include(":feature:share")
include(":feature:notifications")
include(":feature:backup")
include(":feature:settings")
include(":feature:integrations")
include(":feature:widget")
