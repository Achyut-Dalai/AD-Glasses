pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // JetBrains Compose Multiplatform (Skiko native binaries for iOS)
        maven { url = uri("https://maven.packagist.org") }

        // Meta Wearables DAT SDK (requires GitHub token with read:packages scope)
        val localProps = java.util.Properties()
        val localPropsFile = rootDir.resolve("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }
        val githubToken = System.getenv("GITHUB_TOKEN")
            ?: localProps.getProperty("github_token")
            ?: localProps.getProperty("meta_token")
        val githubUsername = System.getenv("GITHUB_ACTOR")
            ?: localProps.getProperty("github_username")
            ?: "Achyut-Dalai"
        if (!githubToken.isNullOrBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                credentials {
                    username = githubUsername
                    password = githubToken
                }
            }
        }
    }
}

rootProject.name = "CyanBridgeManagerApp"
include(":app")
include(":shared")
include(":assistant-role")

// Moonshine Voice (local wrapper module that builds vendored native sources)
include(":moonshine-voice")

// HeyCyan Core - bundled as composite build for easy compilation
val heycyanCoreDir = file("../../heycyan-core")
if (heycyanCoreDir.exists()) {
    includeBuild(heycyanCoreDir)
}
