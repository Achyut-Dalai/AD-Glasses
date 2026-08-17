// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // React Native's integration guide loads RNGP on the buildscript classpath so the
        // legacy root-project plugin below is available to this existing Android build.
        // The included build in settings.gradle.kts supplies the local plugin implementation.
        classpath("com.facebook.react:react-native-gradle-plugin")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.atomicfu) apply false
}

// React Native community Android libraries still read these conventional root-project
// extension properties when selecting their Android SDK/toolchain versions. Keep them in
// one place so autolinked libraries use the same API levels as the AD Glasses app instead
// of silently falling back to legacy defaults (react-native-svg previously fell back < 30).
extra["compileSdkVersion"] = 36
extra["targetSdkVersion"] = 35
extra["minSdkVersion"] = 24
extra["buildToolsVersion"] = "36.0.0"
extra["ndkVersion"] = "27.1.12297006"

// Lets the React Native Gradle plugin coordinate React/Hermes versions across modules.
apply(plugin = "com.facebook.react.rootproject")
