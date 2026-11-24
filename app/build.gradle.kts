plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
}

android {
    namespace = "mcc.app"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "mcc.app"
        minSdk = 23
        // The tablet runs API 23; a system-uid app targeting 23 avoids runtime-permission plumbing.
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 23
        versionCode = 9
        versionName = "1.4.1"
        // test releases (tags vX.Y.Z-test.N) add "-test.N"; the versionCode stays, so the final release installs over it
        versionNameSuffix = providers.gradleProperty("mccVersionSuffix").orNull
        ndk { abiFilters += "armeabi-v7a" }
    }

    signingConfigs {
        create("aospPlatform") {
            // PUBLIC AOSP test key; the MCC firmware is signed with it (see signing/README.txt).
            storeFile = rootProject.file("signing/aosp-platform-test.keystore")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("aospPlatform") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("aospPlatform")
        }
    }

    externalNativeBuild {
        ndkBuild { path = file("src/main/jni/Android.mk") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**") }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":link"))
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
