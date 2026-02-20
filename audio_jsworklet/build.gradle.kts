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
