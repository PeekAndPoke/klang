import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    idea
    kotlin("multiplatform")
    kotlin("plugin.serialization") version Deps.kotlinVersion apply false
    id("com.google.devtools.ksp") version Deps.Ksp.version apply false
    id("org.jetbrains.kotlinx.atomicfu") version Deps.KotlinLibs.atomicfu_version apply false
    id("io.kotest") version Deps.Test.kotest_plugin_version apply false
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

allprojects {
    apply(plugin = "idea")

    idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    repositories {
        mavenCentral()
        // KotlinX
        // maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        // Snapshots
        // maven("https://oss.sonatype.org/content/repositories/snapshots")
        // Local
        // mavenLocal()
    }
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            // This generates index.html
            binaries.executable()
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            // Configures a JavaExec task named "runJvm" and a Gradle distribution for the "main" compilation in this target
            executable {
                mainClass.set("io.peekandpoke.klang.MainKt")
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)
                implementation(Deps.KotlinX.serialization_core)
                implementation(Deps.KotlinX.serialization_json)

                implementation(Deps.Ktor.Client.core)
                implementation(Deps.Ktor.Client.cio)

                implementation(project(":klang"))
                implementation(project(":strudel"))
            }
        }

        commonTest {
            dependencies {
                Deps.Test {
                    commonTestDeps()
                }
            }
        }

        jvmMain {
            dependencies {
                // GraalVM
                implementation(Deps.JavaLibs.GraalVM.polyglot)
                implementation(Deps.JavaLibs.GraalVM.js)

                implementation(Deps.JavaLibs.logback_classic)
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

tasks {
    // Task to copy the audio_be JS output to the root resources as dsp.js
    val copyAudioWorklet = register<Copy>("copyAudioWorklet") {
        group = "build"
        description = "Copies the audio_be JS bundle to jsMain resources as dsp.js"

        // TODO: figure out why the productionWebpack files do not work...

        val prod = false

        if (prod) {
            // Depends on the production webpack build of the backend module
            dependsOn(":audio_be:jsBrowserProductionWebpack")

            val path = project(":audio_be").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable/")

            println("PROD Path ${path.get().asFile.absolutePath}")

            from(path) {
                include("*.*")
            }
        } else {
            // Depends on the production webpack build of the backend module
            dependsOn(":audio_be:jsBrowserDevelopmentWebpack")

            val path = project(":audio_be").layout.buildDirectory.dir("kotlin-webpack/js/developmentExecutable/")

            println("DEV Path ${path.get().asFile.absolutePath}")

            from(path) {
                include("*.*")
            }
        }

        // Copy mapping file too if needed for debugging
        // from(...) { include("*.map"); rename { "dsp.js.map" } }

        into(file("src/jsMain/resources"))
    }

    // Hook this into the standard resource processing
    named("jsProcessResources") {
        dependsOn(copyAudioWorklet)
    }
}
