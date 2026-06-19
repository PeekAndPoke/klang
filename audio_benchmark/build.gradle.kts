@file:Suppress("PropertyName")

plugins {
    idea
    kotlin("multiplatform")
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
        nodejs {
            binaries.executable()
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)

                implementation(project(":common"))
                implementation(project(":audio_bridge"))
                implementation(project(":audio_be"))
                implementation(project(":sprudel"))
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
