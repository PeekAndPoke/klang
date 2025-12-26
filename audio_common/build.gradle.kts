@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.atomicfu")
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

    wasmJs {
        browser()
        binaries.executable()
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
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
