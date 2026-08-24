import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.library") version "9.3.2" apply false
}

val coreVersion = providers.gradleProperty("version").orNull ?: "0.2.0"

allprojects {
    group = "com.heycyan.core"
    version = coreVersion
}

// This is repo-owned core code, so make deprecation and unchecked Java usage
// visible in CI instead of allowing javac to collapse it into a generic note.
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}
