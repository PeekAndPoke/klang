@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
//    id("io.kotest") version Deps.Test.kotest_plugin_version
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

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)
                implementation(Deps.KotlinX.serialization_core)
                implementation(Deps.KotlinX.serialization_json)

                api(Deps.KotlinLibs.Ultra.common)

                api(project(":common"))
                api(project(":klang"))
                api(project(":klangscript"))
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
                // GraalVM
                implementation(Deps.JavaLibs.GraalVM.polyglot)
                implementation(Deps.JavaLibs.GraalVM.js)
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

    val buildStrudelBundle = register<Exec>("buildStrudelBundle") {
        group = "build"
        description = "Builds the Strudel ESM Graal-JS bridge using the shell script"

        workingDir = file("jsbridge")

        // Ensure the script is executable (useful for CI/Linux environments)
        doFirst {
            val script = file("jsbridge/build.sh")
            if (script.exists()) {
                script.setExecutable(true)
            }
        }

        commandLine("./build.sh")

        // Optimization: Only run if the JS source files changed
        inputs.dir("jsbridge")
        outputs.file("src/jvmMain/resources/strudel-entry.mjs")
    }

    // Ensure the bundle is built before resources are processed for the JVM
    named("jvmProcessResources") {
        dependsOn(buildStrudelBundle)
    }
}
