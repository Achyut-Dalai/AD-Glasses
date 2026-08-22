plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val enableAppleTargets = providers.gradleProperty("enableAppleTargets").orNull == "true"

kotlin {
    android {
        namespace = "com.ad_glasses.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    jvm("portability") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    if (enableAppleTargets) {
        iosArm64 {
            binaries.framework {
                baseName = "AD_GLASSESShared"
                isStatic = true
            }
        }
        iosSimulatorArm64 {
            binaries.framework {
                baseName = "AD_GLASSESShared"
                isStatic = false
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            // Compose Multiplatform stopped publishing newer Material Icons Extended
            // after 1.7.3. Keep the final compatibility artifact explicitly while the
            // last device-binding/dashboard symbols move to product-owned vectors.
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.ad_glasses.shared.generated.resources"
    publicResClass = true
}
