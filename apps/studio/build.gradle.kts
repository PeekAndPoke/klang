@file:Suppress("PropertyName")

// The studio (KLANG-00465): the app at klang.finzo.de, the editor, the docs, the tutorials, the tools. Until 2026-11-18
// it was the root project; now it is :apps:studio beside notstrom, and what both use is :apps:common.
//
// The build lays beside the page what it fetches: the band's figures (klangviz's resources), the audio worklet (the
// production webpack output of :audio_jsworklet, served at the page's root as /klang-worklet.js), and version.json
// (written into the build directory, never into the source tree; the deploy names the release after it).
//
// The JVM side runs in the repository's root folder, as it did when this was the root project: its specs read other
// modules' sources by path (workshop/src, sprudel/src), the benchmarks write into docs/benchmarks/, the corpus check
// reads the samples from ./cache, the sample mirror writes into the sibling checkout ../peekandpoke.github.io/klang.

import Deps.Test.configureJvmTests
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("multiplatform")
    // The Spine's stored sections are data classes with a serializer (KLANG-00422)
    kotlin("plugin.serialization")
    // Kotest's spec discovery for the app's browser tests runs through KSP (KLANG-00343)
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

// The apps' own group, as :apps:common has (see there)
group = "$GROUP.apps"
version = VERSION_NAME

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
                mainClass.set("io.peekandpoke.klang.studio.MainKt")
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
                // What the apps share: the built-in songs, the script engine, the live music for the band (KLANG-00465)
                api(project(":apps:common"))
                // The shape and the merge of the stored documents, shared with a server (KLANG-00422)
                api(project(":workshop"))
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
                // The visualizations: the live act (KLANG-00447)
                api(project(":klangviz"))
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
                api(Deps.KotlinLibs.Kraft.addons_browserdetect)
                api(Deps.KotlinLibs.Kraft.addons_marked)
                api(Deps.KotlinLibs.Kraft.addons_pixijs)
                api(Deps.KotlinLibs.Kraft.addons_threejs)
            }
        }

        jsTest {
            dependencies {
                // Kraft's TestBed, to mount the app's components (KLANG-00343)
                implementation(Deps.KotlinLibs.Kraft.testing)
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
    configureJvmTests {
        // In the repository's root folder, where the specs open other modules' sources by path (see the top)
        workingDir = rootDir

        // GeneratedRegistrationOrderSpec reads the KSP output of these modules from their build directory, which is
        // not build/ for user claude (.build-claude/, MC-00031). Read when the test starts: the modules set their
        // build directory after this project is configured.
        listOf("sprudel", "klangscript-libs").forEach { module ->
            val buildDir = project(":$module").layout.buildDirectory
            jvmArgumentProviders.add(CommandLineArgumentProvider {
                listOf("-Dklang.buildDir.$module=${buildDir.get().asFile.path}")
            })
        }

        // DslDocExamplesSpec writes its report into this project's build directory, the same per-user split
        val studioBuildDir = layout.buildDirectory
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Dklang.buildDir.studio=${studioBuildDir.get().asFile.path}")
        })
    }

    // The JVM programs (runJvm, runCli, runSampleMirror, runSongBenchmark) run in the repository's root folder too:
    // their relative paths (docs/benchmarks/, ./cache, ../peekandpoke.github.io/klang, a song file given to the CLI)
    // mean what they meant when this was the root project.
    withType<JavaExec>().configureEach {
        workingDir = rootDir
    }

    // version.json (project + git metadata). Consumed by the frontend: webpack bakes it into index.html
    // (webpack.config.d/02-html-config.js), and it is served at /version.json (VersionController's fallback, and the
    // deploy names the release after it). Into the build directory: it is laid beside the page by jsProcessResources.
    // Shells out to git directly (no extra build dependency); every field degrades to "n/a" on failure.
    val versionDir = layout.buildDirectory.dir("generated/version")

    val versionFile = register("versionFile") {
        description = "Writes version.json (project + git metadata) for the page"
        group = "build"
        // Git state is cheap to read and changes between builds: never cached
        outputs.upToDateWhen { false }
        outputs.dir(versionDir)

        // Read while the build is configured: a task that reads `project` while it runs fails in Gradle 10 (KLANG-00348).
        // The project's name is the build's, "klang-engine", as when this was the root project: the studio shows it
        // ("Engine klang-engine v0.8.15").
        val projectName = rootProject.name
        val projectVersion = project.version.toString()
        val gitDir = rootDir
        val versionJson = versionDir.map { it.file("version.json").asFile }

        doLast {
            fun git(vararg args: String): String? = runCatching {
                val proc = ProcessBuilder(listOf("git", *args))
                    .directory(gitDir)
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
                "project" to projectName,
                "version" to projectVersion,
                "gitBranch" to (git("rev-parse", "--abbrev-ref", "HEAD") ?: na),
                "gitRev" to (git("rev-parse", "--short=8", "HEAD") ?: na),
                "gitDesc" to (git("describe", "--tags", "--always", "--dirty") ?: na),
                "date" to now,
            )

            fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
            val json = fields.entries.joinToString(
                separator = ",\n",
                prefix = "{\n",
                postfix = "\n}\n",
            ) { (k, v) -> """  "$k": "${esc(v)}"""" }

            versionJson.get().apply {
                parentFile.mkdirs()
                writeText(json)
            }
        }
    }

    named<ProcessResources>("jsProcessResources") {
        // the live act's figures are klangviz's resources: the page fetches them, so they are laid beside it (KLANG-00449)
        from(project(":klangviz").file("src/jsMain/resources"))
        // the audio worklet, built for production: the engine fetches it from /klang-worklet.js
        from(project(":audio_jsworklet").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) {
            include("klang-worklet.js", "klang-worklet.js.map")
        }
        dependsOn(":audio_jsworklet:jsBrowserProductionWebpack")
        from(versionFile)
    }

    // The page carries the release's version.json fields (webpack.config.d/02-html-config.js reads them): written first
    named("jsBrowserProductionWebpack") { dependsOn(versionFile) }
    named("jsBrowserDevelopmentWebpack") { dependsOn(versionFile) }

    // The production bundle's name carries its content hash; the development server's bundle is left as it is
    named<KotlinWebpack>("jsBrowserProductionWebpack") {
        mainOutputFileName.set("klang-engine.[contenthash].js")
    }

    // CLI entry point (separate from the playground runJvm)
    register<JavaExec>("runCli") {
        group = "application"
        description = "Run the Klang CLI"
        mainClass.set("io.peekandpoke.klang.studio.CliKt")

        val jvmMain = kotlin.jvm().compilations["main"]
        classpath = jvmMain.runtimeDependencyFiles + jvmMain.output.allOutputs
        dependsOn("jvmMainClasses")
    }

    // Sample mirror: downloads all sample sets into the sibling static-hosting checkout.
    // Usage: ./gradlew :apps:studio:runSampleMirror                     (mirror into ../peekandpoke.github.io/klang)
    //        ./gradlew :apps:studio:runSampleMirror --args="--verify"   (offline check: manifest entries vs files on disk)
    // Re-run until the report shows zero failures, then upload via console/deploy-samples-finzo.sh
    register<JavaExec>("runSampleMirror") {
        group = "application"
        description = "Mirror all sample sets into ../peekandpoke.github.io/klang (see SampleMirrorMain)"
        mainClass.set("io.peekandpoke.klang.studio.SampleMirrorMainKt")

        val jvmMain = kotlin.jvm().compilations["main"]
        classpath = jvmMain.runtimeDependencyFiles + jvmMain.output.allOutputs
        dependsOn("jvmMainClasses")
    }

    // Song-level CPU benchmark: compiles real song code, renders offline, times per-block.
    // Usage: ./gradlew :apps:studio:runSongBenchmark            (all suites)
    //        ./gradlew :apps:studio:runSongBenchmark --args=ladders
    register<JavaExec>("runSongBenchmark") {
        group = "application"
        description = "Run the song-level CPU benchmark (voices / ladders / experiments / frozen songs)"
        mainClass.set("io.peekandpoke.klang.studio.SongBenchmarkMainKt")

        val jvmMain = kotlin.jvm().compilations["main"]
        classpath = jvmMain.runtimeDependencyFiles + jvmMain.output.allOutputs
        dependsOn("jvmMainClasses")
    }
}
