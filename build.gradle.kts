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
        mavenLocal()
    }
}

kotlin {
    js {
        compilerOptions {
            target.set("es2015")
        }

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
                api(project(":sprudel"))
                api(project(":klangblocks"))
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
                // Automatically inject Webpack bundles (with hashes!) into index.html
                implementation(devNpm("html-webpack-plugin", "5.6.0"))

                api(project(":klangui"))
                api(project(":klangscript-ui"))
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
                api(Deps.KotlinLibs.Kraft.addons_browserdetect)
                api(Deps.KotlinLibs.Kraft.addons_marked)
            }
        }

        jvmMain {
            dependencies {
                // GraalVM (for playground / GraalSprudelCompiler)
                implementation(Deps.JavaLibs.GraalVM.polyglot)
                implementation(Deps.JavaLibs.GraalVM.js)

                implementation(Deps.JavaLibs.logback_classic)

                // CLI framework (needed by Cli.kt entry point)
                implementation(Deps.KotlinLibs.clikt)
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
//        dependsOn(copyWorkletDev)
        dependsOn(copyWorkletProd)

        // Ensure we don't accidentally cache an old version of the worklet
        outputs.upToDateWhen { false }
    }

    // Specifically target the production Webpack task to add the content hash
    // This leaves the development server completely untouched!
    named<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>("jsBrowserProductionWebpack") {
        mainOutputFileName.set("klang-engine.[contenthash].js")
    }

    // CLI entry point (separate from the playground runJvm)
    register<JavaExec>("runCli") {
        group = "application"
        description = "Run the Klang CLI"
        mainClass.set("io.peekandpoke.klang.CliKt")

        val jvmMain = kotlin.jvm().compilations["main"]
        classpath = jvmMain.runtimeDependencyFiles + jvmMain.output.allOutputs
        dependsOn("jvmMainClasses")
    }
}
