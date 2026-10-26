@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("io.kotest")
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
                // so no serialization plugin/deps here. See KLANG-00105 (wireformat enhancements).
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
// into jsMain (NOT kspCommonMainMetadata). See KLANG-00103 (worklet codec ksp).
dependencies {
    add("kspJs", project(":audio-wire-codec-ksp"))
}

tasks {
    configureJvmTests()
}
