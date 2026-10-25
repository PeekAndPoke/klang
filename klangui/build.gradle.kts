@file:Suppress("PropertyName")

plugins {
    kotlin("multiplatform")
    // Kotest's spec discovery on JS runs through KSP, as in the other modules with browser tests.
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser {
            commonWebpackConfig { devtool = "source-map" }
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                api(project(":audio_bridge"))
                api(project(":klangscript"))
                api(project(":tones"))
                api(project(":workshop"))
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
                api(Deps.KotlinLibs.Kraft.addons_marked)
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
