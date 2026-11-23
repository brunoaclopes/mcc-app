plugins {
    id("com.android.library")
}

// Shared by the tablet app and the phone app: theme, icons, widgets, translations, recipe store and images.
android {
    namespace = "mcc.common"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}

dependencies {
    api(project(":recipe"))
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
