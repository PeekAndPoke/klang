@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    // KSP plugin stays applied for kotest's spec discovery; the module runs NO symbol processor of
    // its own any more (the stdlib and its generated registration live in :klangscript-libs).
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
            commonWebpackConfig {
                devtool = "source-map"
            }
        }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.addAll(
                    "-Xir-property-lazy-initialization=false",
                    "-Xir-minimized-member-names=false",
                )
            }
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":common"))
                api(project(":klangscript-annotations"))
                implementation(kotlin("reflect"))
                implementation(Deps.KotlinX.coroutines_core)
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

        jsTest {
            dependencies {
                Deps.Test {
                    jsTestDeps()
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
