@file:Suppress("PropertyName")

plugins {
    idea
    kotlin("multiplatform")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

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

    // The allocation check finds the node bundle in this module's build directory, which is not build/ for user
    // claude (.build-claude/, MC-00031)
    val buildDir = layout.buildDirectory
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dklang.buildDir.audio_benchmark=${buildDir.get().asFile.path}")
    })
}
