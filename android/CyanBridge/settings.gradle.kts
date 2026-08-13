pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // JetBrains Compose Multiplatform
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }

        // Meta Wearables DAT SDK (requires GitHub token with read:packages scope)
        val localProps = java.util.Properties()
        val localPropsFile = rootDir.resolve("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }
        val githubToken = System.getenv("GITHUB_TOKEN")
            ?: localProps.getProperty("github_token")
        if (!githubToken.isNullOrBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
                credentials {
                    username = "achyutdalai"
                    password = githubToken
                }
            }
        }
    }
}
rootProject.name = "ADGlassesManagerApp"
include(":app")
include(":shared")

// Moonshine Voice (local wrapper module that builds vendored native sources)
include(":moonshine-voice")

// AD Glasses Core - bundled as composite build for easy compilation
val ad_glassesCoreDir = file("../../ad_glasses-core")
if (ad_glassesCoreDir.exists()) {
    includeBuild(ad_glassesCoreDir)
}
