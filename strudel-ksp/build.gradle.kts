plugins {
    kotlin("jvm")
    `kotlin-kapt`
}

dependencies {
    // KSP API for symbol processing
    implementation(Deps.Ksp.symbol_processing)

    // Google AutoService for processor registration
    implementation(Deps.JavaLibs.Google.auto_service)

    // Access to the docs model
    implementation(project(":klangscript"))

    // Add kapt dependency for JVM target
    add("kapt", Deps.JavaLibs.Google.auto_service)
    add("kaptTest", Deps.JavaLibs.Google.auto_service)
}

// KSP processors must use a lower JVM target for compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(Deps.jvmTargetVersion)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
