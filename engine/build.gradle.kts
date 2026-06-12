// PURE KOTLIN MODULE - no Android dependencies. Everything here must run on a plain JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    // Headless balance simulator: prints check-in player playthrough tables (see SIMULATION.md).
    mainClass.set("com.heirloom.engine.runner.HeadlessRunnerKt")
}

tasks.named<JavaExec>("run") {
    // SIMULATION.md lands in the repository root.
    workingDir = rootDir
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
