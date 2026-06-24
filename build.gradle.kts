import Deps.Test.configureJvmTests
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                api(Deps.KotlinLibs.Kraft.addons_pixijs)
                api(Deps.KotlinLibs.Kraft.addons_threejs)
            }
        }

        jvmMain {
            dependencies {
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
    // Use JUnit Platform so kotest tests in src/jvmTest/ are discovered.
    configureJvmTests()

    // Generates src/jsMain/resources/version.json (project + git metadata).
    // Consumed by the frontend (webpack injects it into index.html; see
    // webpack.config.d/02-html-config.js + VersionController). Shells out to git
    // directly (no extra build dependency); every field degrades to "n/a" on failure.
    val versionFile by registering {
        description = "Writes version.json (project + git metadata) into the JS resources"
        group = "build"
        // Git state is cheap to read and changes between builds — never cache.
        outputs.upToDateWhen { false }

        doLast {
            fun git(vararg args: String): String? = runCatching {
                val proc = ProcessBuilder(listOf("git", *args))
                    .directory(rootDir)
                    .start()
                val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
                proc.waitFor()
                out.takeIf { proc.exitValue() == 0 && it.isNotEmpty() }
            }.getOrNull()

            val na = "n/a"
            val now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            // `describe --always` falls back to the short hash when there are no tags
            val fields = linkedMapOf(
                "project" to project.name,
                "version" to project.version.toString(),
                "gitBranch" to (git("rev-parse", "--abbrev-ref", "HEAD") ?: na),
                "gitRev" to (git("rev-parse", "--short=8", "HEAD") ?: na),
                "gitDesc" to (git("describe", "--tags", "--always") ?: na),
                "date" to now,
            )

            fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
            val json = fields.entries.joinToString(
                separator = ",\n",
                prefix = "{\n",
                postfix = "\n}\n",
            ) { (k, v) -> """  "$k": "${esc(v)}"""" }

            file("src/jsMain/resources/version.json").apply {
                parentFile.mkdirs()
                writeText(json)
            }
        }
    }

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
        // Refresh version.json before resources are processed / webpack runs
        dependsOn(versionFile)

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
