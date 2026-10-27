@file:Suppress("PropertyName")

plugins {
    kotlin("multiplatform")
    // Kotest's spec discovery on JS runs through KSP, as in the other modules with browser tests.
    id("com.google.devtools.ksp")
    id("io.kotest")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

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

                // Kraft's TestBed, to render the editor's context menu (the Sprudel tool's item, KLANG-00374)
                implementation(Deps.KotlinLibs.Kraft.testing)
            }
        }
    }
}
