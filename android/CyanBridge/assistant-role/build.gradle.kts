plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fersaiyan.cyanbridge.assistant"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
