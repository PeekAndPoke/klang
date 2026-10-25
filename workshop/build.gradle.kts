@file:Suppress("PropertyName")

// The workshop: what Klang builds for Kraft before it moves there (KLANG-00325). It depends on Kraft and Ultra only,
// never on a Klang module, so nothing of Klang creeps in and the move is a copy.

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
