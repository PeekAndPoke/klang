@file:Suppress("PropertyName")

plugins {
    idea
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser {
            binaries.executable()
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
        withJava()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)
                implementation(Deps.KotlinX.serialization_core)
                implementation(Deps.KotlinX.serialization_json)

                implementation(project(":common"))
                implementation(project(":audio_bridge"))
                implementation(project(":audio_be"))
            }
        }

        jsMain {
            dependencies {
            }
        }

        jvmMain {
            dependencies {
            }
        }
    }
}

// Gradle task to run benchmarks on JVM: ./gradlew :audio_benchmark:jvmRun
tasks.register<JavaExec>("jvmRun") {
    group = "benchmark"
    description = "Run audio benchmarks on JVM"
    mainClass.set("io.peekandpoke.klang.audio_benchmark.MainKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
            kotlin.jvm().compilations["main"].output.allOutputs
}
