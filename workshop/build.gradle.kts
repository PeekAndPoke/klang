@file:Suppress("PropertyName")

// The workshop: what Klang builds for Kraft before it moves there (KLANG-00325). It depends on Kraft and Ultra only,
// never on a Klang module, so nothing of Klang creeps in and the move is a copy.

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

    // The storage pieces are shared code: a server keeps the same documents and merges them the same way
    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                api(Deps.KotlinLibs.Ultra.streams)
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
                api(Deps.KotlinLibs.Kraft.core)
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
