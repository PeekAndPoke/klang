@file:Suppress("PropertyName")

// Notstrom (KLANG-00465): the band's own app. One page: a start screen, then the band walks onto the stage and plays
// Der Schmetterling with the engine's sound. What it shows is klangviz's; what reads the song for the band and plays
// it for the show is the apps' common code (:apps:common).
//
// The build lays beside the page what it fetches: the band's figures (klangviz's resources), the audio worklet (the
// production webpack output of :audio_jsworklet, served at the page's root as /klang-worklet.js), and version.json
// (written into the build directory, never into the source tree; the deploy names the release after it).

import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("multiplatform")
    // The machine test's result, kept in the browser, is a data class with a serializer (StoredTest)
    kotlin("plugin.serialization")
    // Kotest's spec discovery on JS runs through KSP, as in the other modules with browser tests.
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
        browser {
            commonWebpackConfig { devtool = "source-map" }
            binaries.executable()
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":apps:common"))
                implementation(project(":klangviz"))
                // Which browser this is: the apps warn outside desktop Chrome (BrowserCheck)
                implementation(Deps.KotlinLibs.Kraft.addons_browserdetect)
                // The machine test's result, kept in the browser as JSON (StoredTest)
                implementation(Deps.KotlinX.serialization_core)
                implementation(Deps.KotlinX.serialization_json)
                // Injects the bundle (its name with a content hash) into index.html
                implementation(devNpm("html-webpack-plugin", "5.6.0"))
            }
        }

        jsTest {
            dependencies {
                Deps.Test {
                    jsTestDeps()
                }
            }
        }
    }
}

tasks {
    // version.json (project + git metadata), the fields the studio's has: the deploy reads the version and the git
    // revision from it. Into the build directory: it is laid beside the page by jsProcessResources.
    val versionDir = layout.buildDirectory.dir("generated/version")

    val versionFile = register("versionFile") {
        description = "Writes version.json (project + git metadata) for the page"
        group = "build"
        // Git state is cheap to read and changes between builds: never cached
        outputs.upToDateWhen { false }
        outputs.dir(versionDir)

        // Read while the build is configured: a task that reads `project` while it runs fails in Gradle 10
        val projectName = project.name
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

    named<Copy>("jsProcessResources") {
        // the page's template is read by webpack from the sources (webpack.config.d/01-page.js), not served
        exclude("index_template.html")
        // the band's figures: the page fetches them from liveact/notstrom/
        from(project(":klangviz").file("src/jsMain/resources"))
        // the audio worklet, built for production: the engine fetches it from /klang-worklet.js
        from(project(":audio_jsworklet").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) {
            include("klang-worklet.js", "klang-worklet.js.map")
        }
        dependsOn(":audio_jsworklet:jsBrowserProductionWebpack")
        from(versionFile)
    }

    // The development and the production compile sync into the same package folder, and each webpack reads it: one
    // build that makes both runs them in order (as the worklet's build does, KLANG-00348).
    named("jsProductionExecutableCompileSync") {
        mustRunAfter("jsDevelopmentExecutableCompileSync", "jsBrowserDevelopmentWebpack")
    }

    named("jsBrowserProductionWebpack") {
        mustRunAfter("jsDevelopmentExecutableCompileSync")
    }

    // The page carries the release's version.json fields (webpack.config.d/01-page.js reads them): written first
    named("jsBrowserProductionWebpack") { dependsOn(versionFile) }
    named("jsBrowserDevelopmentWebpack") { dependsOn(versionFile) }

    // The production bundle's name carries its content hash: a new release is a new name for browsers and caches,
    // and the engine takes the hash for the worklet's address too (its cache buster)
    named<KotlinWebpack>("jsBrowserProductionWebpack") {
        mainOutputFileName.set("notstrom.[contenthash].js")
    }
}
