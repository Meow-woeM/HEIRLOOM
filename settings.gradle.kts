pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        // mavenCentral first: the pure-JVM :engine module resolves entirely from it,
        // so engine builds/tests work even where Google's Maven repo is unreachable.
        mavenCentral()
        google()
    }
}

rootProject.name = "heirloom"

include(":engine")

// The :app module needs the Android SDK and Google's Maven repository. Include it
// only when an SDK is locatable so the engine can be built and tested headlessly
// (CI containers, plain JVM environments) without Android tooling.
val localProps = file("local.properties")
val hasSdkDir = localProps.exists() && localProps.readText().lineSequence().any {
    it.trim().startsWith("sdk.dir=")
}
val hasAndroidSdk = hasSdkDir || System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found - building :engine only. Set sdk.dir in local.properties or ANDROID_HOME to include :app.")
}
