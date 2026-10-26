@file:Suppress("PropertyName")

plugins {
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

            webpackTask {
                mainOutputFileName = "klang-worklet.js"
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
    // The development and the production compile sync into the same package folder, and each webpack reads it.
    // Gradle 9 refuses that unordered (KLANG-00348), so one build runs them as: the development sync, its webpack,
    // the production sync, its webpack; each bundle reads its own code. The webpack tasks only: the worklet's own dev
    // servers (`*Run`) are not ordered, nobody runs them beside the app.
    named("jsProductionExecutableCompileSync") {
        mustRunAfter("jsDevelopmentExecutableCompileSync", "jsBrowserDevelopmentWebpack")
    }

    named("jsBrowserProductionWebpack") {
        mustRunAfter("jsDevelopmentExecutableCompileSync")
    }
}
