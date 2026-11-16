@file:Suppress("PropertyName")

// The visualizations (KLANG-00446): what is seen while the music plays, beyond the app's own pages. The first is
// the live act: a band on a stage. The arithmetic (a body's poses, the lights' moods, the camera's rounds) is shared
// code, so the JVM runs its specs; what draws is the JS part, on three.js through Kraft's addon and the workshop's
// types. It depends on no part of the app: the app, or the small demo app beside it, hands it what it shows.

import Deps.Test.configureJvmTests

plugins {
    kotlin("multiplatform")
    // Kotest's spec discovery on JS runs through KSP, as in the other modules with browser tests.
    id("com.google.devtools.ksp")
    id("io.kotest")
    kotlin("plugin.serialization")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser {
            commonWebpackConfig { devtool = "source-map" }
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                // A recorded song's events and loudness are read from JSON
                api(Deps.KotlinX.serialization_core)
                api(Deps.KotlinX.serialization_json)
            }
        }

        commonTest {
            dependencies {
                Deps.Test {
                    commonTestDeps()
                }
            }
        }

        jvmTest {
            dependencies {
                Deps.Test {
                    jvmTestDeps()
                }
            }
        }

        jsMain {
            dependencies {
                api(project(":workshop"))
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.addons_threejs)
            }
        }

        jsTest {
            dependencies {
                Deps.Test {
                    jsTestDeps()
                }
                implementation(Deps.KotlinLibs.Kraft.testing)
            }
        }
    }
}

tasks {
    configureJvmTests()
}
