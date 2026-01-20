@file:Suppress("PropertyName")

plugins {
    idea
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

kotlin {
    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
        // JVM-only for notebooks
    }

    sourceSets {
        jvmMain {
            dependencies {
                // Core Klang modules
                api(project(":klang"))
                api(project(":klangscript"))
                api(project(":strudel"))
                api(project(":audio_bridge"))
                api(project(":tones"))

                // All transitive dependencies that notebooks need
                api(Deps.KotlinX.coroutines_core)
                api(Deps.KotlinX.serialization_core)
                api(Deps.KotlinX.serialization_json)
                api(Deps.KotlinLibs.better_parse)

                // GraalVM for script execution
                api(Deps.JavaLibs.GraalVM.polyglot)
                api(Deps.JavaLibs.GraalVM.js)
            }
        }

        jvmTest {
            dependencies {
                Deps.Test {
                    jvmTestDeps()
                }
            }
        }
    }
}
