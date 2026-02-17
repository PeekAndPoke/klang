@file:Suppress("PropertyName")

plugins {
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

            webpackTask {
                mainOutputFileName = "klang-worklet.js"
                cssSupport { enabled.set(false) }
            }
        }

        compilerOptions {
            // This forces Kotlin to generate "class X extends Y" instead of functions.
            // This is required for AudioWorklets, WebComponents, etc.
            target.set("es2015")
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                api(project(":audio_be"))
                api(project(":audio_bridge"))
            }
        }
    }
}

tasks {
    // Copy compiled worklet to main project's resources
    val copyWorkletToResources by registering(Copy::class) {
        description = "Copy compiled AudioWorklet to main project resources"

        from("build/kotlin-webpack/js/productionExecutable")
        into("${project.rootDir}/src/jsMain/resources")
        include("klang-worklet.js")
    }

    val copyWorkletToResourcesDev by registering(Copy::class) {
        description = "Copy development AudioWorklet to main project resources"
        from("build/kotlin-webpack/js/developmentExecutable")
        into("${project.rootDir}/src/jsMain/resources")
        include("klang-worklet.js")
    }

    // Auto-copy after webpack bundles
    named("jsBrowserProductionWebpack") {
        mustRunAfter("jsDevelopmentExecutableCompileSync")
        finalizedBy(copyWorkletToResources)
    }

    named("jsBrowserDevelopmentWebpack") {
        mustRunAfter("jsProductionExecutableCompileSync")
        finalizedBy(copyWorkletToResourcesDev)
    }
}
