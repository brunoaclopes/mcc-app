pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mcc-app"
include(":machine")
include(":recipe")
include(":link")
include(":common")
include(":app")
include(":phone")
