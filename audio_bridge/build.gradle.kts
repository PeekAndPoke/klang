@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                // @Serializable wire types need core + the plugin. JSON is only used by tests (the worklet now
                // uses the KSP-generated codec) — so serialization_json lives in commonTest, not here.
                implementation(Deps.KotlinX.serialization_core)

                api(project(":common"))
                api(project(":tones"))
            }
        }

        commonTest {
            dependencies {
                implementation(Deps.KotlinX.serialization_json)
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
