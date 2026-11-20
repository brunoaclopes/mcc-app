plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":machine"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
    // Validates every converted recipe in the recipe repository, when it is checked out next to this project.
    systemProperty("mcc.recipes.dir", (findProperty("mcc.recipes.dir") as String?) ?: rootProject.file("recipe-repo/recipes").absolutePath)
    testLogging { events("failed") }
}
