plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

import java.util.Properties

// Phone companion app: watches the machine over the same MQTT broker as Home Assistant, notifies, browses recipes.

/**
 * The release key (docs/RELEASING.md). GitHub's Release workflow passes it in MCC_KEYSTORE_FILE, MCC_KEYSTORE_PASSWORD,
 * MCC_KEY_ALIAS and MCC_KEY_PASSWORD; on the maintainer's computer it comes from ~/.mcc-release/keystore.properties
 * (or the file MCC_SIGNING_PROPERTIES names), with the keys storeFile, storePassword, keyAlias and keyPassword.
 * Without either, release builds are signed with the local debug key, so anyone can still build and install.
 */
val releaseKey: Map<String, String>? = run {
    System.getenv("MCC_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file ->
        return@run mapOf("storeFile" to file, "storePassword" to System.getenv("MCC_KEYSTORE_PASSWORD").orEmpty(),
            "keyAlias" to System.getenv("MCC_KEY_ALIAS").orEmpty(), "keyPassword" to System.getenv("MCC_KEY_PASSWORD").orEmpty())
    }
    val f = file(System.getenv("MCC_SIGNING_PROPERTIES") ?: "${System.getProperty("user.home")}/.mcc-release/keystore.properties")
    if (!f.isFile) return@run null
    val p = Properties().apply { f.inputStream().use { load(it) } }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { p.getProperty(it).orEmpty() }
}

android {
    namespace = "mcc.phone"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        // the package name Android knows the app by (the Kotlin code stays in package mcc.phone)
        applicationId = "io.github.brunoaclopes.mcc.phone"
        minSdk = 29
        targetSdk = 36
        versionCode = 20
        versionName = "0.7.2"
        // test releases (tags vX.Y.Z-test.N) add "-test.N"; the versionCode stays, so the final release installs over it
        versionNameSuffix = providers.gradleProperty("mccVersionSuffix").orNull
    }

    signingConfigs {
        releaseKey?.let { k ->
            create("release") {
                storeFile = file(k.getValue("storeFile"))
                storePassword = k.getValue("storePassword")
                keyAlias = k.getValue("keyAlias")
                keyPassword = k.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // the release key when it is set up (above), else the local debug key
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { localeFilters += listOf("en", "pt") }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**", "META-INF/AL2.0", "META-INF/LGPL2.1") }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":link"))
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    // splash screen, fold postures (tabletop), home-screen widget
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
}
