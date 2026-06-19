@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
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
            binaries.executable()

            webpackTask {
                cssSupport { enabled.set(false) }
            }
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                // Wire types are serialized by the KSP-generated codec (`:audio-wire-codec-ksp`), not kotlinx —
                // so no serialization plugin/deps here. See docs/tasks/wireformat-enhancements.md.
                api(project(":common"))
                api(project(":tones"))
            }
        }

        commonTest {
            dependencies {
                Deps.Test {
                    commonTestDeps()
                }
            }
        }

        jvmMain {
            dependencies {
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

// Worklet wire-codec generation — JS-only (codec uses `dynamic`), so applied to the JS target and generated
// into jsMain (NOT kspCommonMainMetadata). See docs/tasks/worklet-codec-ksp.md.
dependencies {
    add("kspJs", project(":audio-wire-codec-ksp"))
}

tasks {
    configureJvmTests()
}
