@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
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
                implementation(Deps.KotlinX.serialization_core)
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

tasks {
    configureJvmTests()
}
