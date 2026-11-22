plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

// Phone companion app: watches the machine over the same MQTT broker as Home Assistant, notifies, browses recipes.
android {
    namespace = "mcc.phone"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "mcc.phone"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "0.4.1"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // sideloaded builds: signed with the local debug key unless you configure your own
            signingConfig = signingConfigs.getByName("debug")
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
