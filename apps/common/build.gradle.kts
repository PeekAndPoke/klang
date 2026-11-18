@file:Suppress("PropertyName")

// What the apps share (KLANG-00465): the built-in songs and jingles with Song, the script engine the apps compile songs
// with, and the live music for the band (what reads a playing song ahead and feeds the live act). The studio and
// notstrom depend on it; it depends on no app.

import Deps.Test.configureJvmTests

plugins {
    kotlin("multiplatform")
    // Kotest's spec discovery on JS runs through KSP, as in the other modules with browser tests.
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

// The apps have a group of their own: this project is named "common" as the top-level :common is, and two projects
// with the same group and name are one library to Gradle and to Kotlin (a klib's unique name is group:name).
group = "$GROUP.apps"
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
                api(project(":klang"))
                // sprudel brings the script language and its standard library (stdlibLib)
                api(project(":sprudel"))
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
                // The live act, which the live music feeds
                api(project(":klangviz"))
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
    configureJvmTests()
}
