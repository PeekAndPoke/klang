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
        }
    }

//    wasmJs {
//        browser {
//            binaries.executable()
//        }
//    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)

                implementation(Deps.KotlinLibs.Ultra.streams)

                api(project(":common"))
                api(project(":audio_bridge"))
            }
        }

        commonTest {
            dependencies {
                Deps.Test {
                    commonTestDeps()
                }
            }
        }

        jsMain {
            dependencies {
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
    configureJvmTests {
        // The click-hunt harness (GuitarClickHuntTest, tag ClickHunt) asserts nothing and is out of the default run
        // (test consolidation, 2026-09-27). Run it with -Pkotest.tags=ClickHunt; any other tag expression also works.
        systemProperty("kotest.tags", project.findProperty("kotest.tags")?.toString() ?: "!ClickHunt")
    }
}
