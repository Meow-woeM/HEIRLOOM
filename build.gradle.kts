// Root build file. Plugin versions come from gradle/libs.versions.toml.
// All plugins are declared here with `apply false` so their versions resolve in a
// single place. The Kotlin JVM and Android plugins share the same underlying
// artifact, so declaring a version in both the root and a subproject triggers a
// "plugin already on the classpath with an unknown version" classpath conflict.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
