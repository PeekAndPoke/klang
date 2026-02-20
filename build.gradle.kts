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
                devtool = "source-map"
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
                api(Deps.KotlinX.coroutines_core)
                api(Deps.KotlinX.serialization_core)
                api(Deps.KotlinX.serialization_json)

                api(Deps.Ktor.Client.core)
                api(Deps.Ktor.Client.cio)

                api(project(":klang"))
                api(project(":strudel"))
            }
        }

        commonTest {
            dependencies {
                Deps.Test {
                    commonTestDeps()
                }
            }
        }

        jsMain {
            dependencies {
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
                api(Deps.KotlinLibs.Kraft.addons_browserdetect)

                // CodeMirror 6
                api(Deps.Npm { codemirrorState() })
                api(Deps.Npm { codemirrorView() })
                api(Deps.Npm { codemirrorCommands() })
                api(Deps.Npm { codemirrorLanguage() })
                api(Deps.Npm { codemirrorLangJavascript() })
                api(Deps.Npm { codemirrorBasicSetup() })
                api(Deps.Npm { codemirrorLint() })
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
//    named("jsProcessResources") {
//        val taskNames = gradle.startParameter.taskNames
//        // Determine if we are in a production flow based on the requested tasks
//        val isProduction = taskNames.any {
//            it.contains("Distribution", ignoreCase = true) ||
//                    it.contains("Production", ignoreCase = true) ||
//                    it.endsWith("build") ||
//                    it.endsWith("assemble")
//        }
//
//        if (isProduction) {
//            dependsOn(":audio_jsworklet:copyWorkletToResources")
//        } else {
//            dependsOn(":audio_jsworklet:copyWorkletToResourcesDev")
//        }
//    }

    val copyWorkletDev by registering(Copy::class) {
        dependsOn(":audio_jsworklet:jsBrowserDevelopmentWebpack")
        from(project(":audio_jsworklet").layout.buildDirectory.dir("kotlin-webpack/js/developmentExecutable"))
        into(layout.projectDirectory.dir("src/jsMain/resources"))
        include("klang-worklet.js", "klang-worklet.js.map")
    }

    val copyWorkletProd by registering(Copy::class) {
        dependsOn(":audio_jsworklet:jsBrowserProductionWebpack")
        from(project(":audio_jsworklet").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable"))
        into(layout.projectDirectory.dir("src/jsMain/resources"))
        include("klang-worklet.js", "klang-worklet.js.map")
    }


    // Force the worklet to be built and copied before resources are processed
    named<ProcessResources>("jsProcessResources") {
        // NOTICE: For now always dev, as the production build does not work yet
        dependsOn(copyWorkletDev)

        // Ensure we don't accidentally cache an old version of the worklet
        outputs.upToDateWhen { false }
    }
}
