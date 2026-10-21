@file:Suppress("PropertyName")

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
        // As the app compiles: ES classes, so Kotlin can extend CodeMirror's (the bookmarks' gutter marker)
        compilerOptions {
            target.set("es2015")
        }

        browser {
            commonWebpackConfig { devtool = "source-map" }
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                api(project(":klangjs"))
                api(project(":klangscript"))
                api(project(":klangscript-libs"))
                api(project(":klangui"))
                api(Deps.KotlinLibs.Kraft.core)
                api(Deps.KotlinLibs.Kraft.semanticui)
            }
        }

        jsTest {
            dependencies {
                Deps.Test {
                    jsTestDeps()
                }
            }
        }
    }
}
