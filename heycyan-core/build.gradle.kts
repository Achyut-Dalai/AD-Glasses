plugins {
    id("com.android.library") version "9.3.1" apply false
}

val coreVersion = providers.gradleProperty("version").orNull ?: "0.2.0"

allprojects {
    group = "com.heycyan.core"
    version = coreVersion
}
