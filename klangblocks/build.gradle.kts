@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests

plugins {
    idea
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
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
    jvm {}

    sourceSets {
        commonMain {
            dependencies {
                api(Deps.KotlinLibs.Mutator.core)
                api(project(":klangscript"))
            }
        }

        commonTest {
            dependencies {
                Deps.Test { commonTestDeps() }
            }
        }

        jsMain {
            dependencies {
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
            }
        }

        jvmMain { dependencies {} }

        jvmTest {
            dependencies {
                Deps.Test { jvmTestDeps() }
            }
        }
    }
}

dependencies {
    add("kspJvm", Deps.KotlinLibs.Mutator.ksp)
    add("kspJs", Deps.KotlinLibs.Mutator.ksp)
}

tasks {
    configureJvmTests()
}
