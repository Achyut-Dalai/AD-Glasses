plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val enableAppleTargets = providers.gradleProperty("enableAppleTargets").orNull == "true"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    // A second host target keeps common code genuinely multiplatform-testable while
    // Apple framework linking and vendor integration remain behind the iOS gate.
    jvm("portability") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    // Apple targets are opt-in so the Android/Linux build remains usable while
    // the framework is compiled and linked from Xcode on macOS.
    if (enableAppleTargets) {
        iosX64 {
            binaries.framework {
                baseName = "CyanBridgeShared"
                isStatic = true
            }
        }
        iosArm64 {
            binaries.framework {
                baseName = "CyanBridgeShared"
                isStatic = true
            }
        }
        iosSimulatorArm64 {
            binaries.framework {
                baseName = "CyanBridgeShared"
                isStatic = true
            }
        }

    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.fersaiyan.cyanbridge.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
