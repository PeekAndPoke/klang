@file:Suppress("PropertyName")

import Deps.Test.configureJvmTests
import java.util.zip.ZipFile

plugins {
    idea
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("io.kotest") version Deps.Test.kotest_plugin_version
}

val GROUP: String by project
val VERSION_NAME: String by project

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser {
            testTask {}
        }
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        jsMain {
            dependencies {
                api(project(":klangui"))
            }
        }

        commonMain {
            dependencies {
                implementation(Deps.KotlinX.coroutines_core)

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

        jvmTest {
            dependencies {
                // GraalVM — only the JS-compatibility differential-test oracle uses it
                // (sprudel/src/jvmTest/kotlin/graal). It is deliberately NOT a jvmMain
                // dependency, so the production jar never carries GraalVM or the vendored
                // Strudel bundle. See docs/tasks-archive/.../copyright-audit-04-*.
                implementation(Deps.JavaLibs.GraalVM.polyglot)
                implementation(Deps.JavaLibs.GraalVM.js)

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
        description = "Builds the vendored Strudel ESM bundle used ONLY by the JS-compat test oracle"

        workingDir = file("jsbridge")

        // Ensure the script is executable (useful for CI/Linux environments)
        doFirst {
            val script = file("jsbridge/build.sh")
            if (script.exists()) {
                script.setExecutable(true)
            }
        }

        commandLine("./build.sh")

        // Optimization: Only run if the JS source files changed. The bundle is a generated,
        // git-ignored, test-only artifact — it is built on the fly into the test resources.
        inputs.dir("jsbridge")
        outputs.file("src/jvmTest/resources/strudel-bundle.mjs")
    }

    // Build the bundle on the fly before the JVM TEST resources are processed (test-only oracle).
    named("jvmTestProcessResources") {
        dependsOn(buildStrudelBundle)
    }

    // Copyright guard: the vendored verbatim Strudel bundle (AGPL) is a TEST-ONLY oracle. This
    // assertion FAILS the build if it ever escapes the jvmTest source set into a production (main)
    // source set or the assembled JVM jar — so it can never reach a distributable / proprietary
    // artifact. Wired into `check` so CI enforces it. See jsbridge/README.md and
    // docs/tasks-archive/.../copyright-audit-04-*.
    val verifyNoVendoredStrudelInProduction = register("verifyNoVendoredStrudelInProduction") {
        group = "verification"
        description = "Fails if the vendored Strudel bundle leaks out of jvmTest into a production artifact"

        dependsOn("jvmJar")

        doLast {
            val offenders = mutableListOf<String>()

            // 1. The bundle must live ONLY under src/jvmTest — never in a main source set.
            for (rel in listOf("src/commonMain", "src/jsMain", "src/jvmMain")) {
                val dir = project.file(rel)
                if (!dir.exists()) continue
                for (f in dir.walkTopDown()) {
                    if (f.isFile && f.name.startsWith("strudel-bundle")) {
                        offenders += f.relativeTo(project.projectDir).path
                    }
                }
            }

            // 2. The assembled production JVM jar(s) must not contain the bundle.
            val libsDir = project.layout.buildDirectory.dir("libs").get().asFile
            val jars = libsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jar") && it.name.contains("-jvm") }.orEmpty()
            for (jar in jars) {
                ZipFile(jar).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entryName = entries.nextElement().name
                        if (entryName.contains("strudel-bundle")) {
                            offenders += jar.name + "!" + entryName
                        }
                    }
                }
            }

            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "Vendored Strudel (AGPL) bundle leaked into a production artifact — it must stay test-only " +
                            "(src/jvmTest/resources, git-ignored; see jsbridge/README.md). Offenders:\n  " +
                            offenders.joinToString("\n  ")
                )
            }
        }
    }

    named("check") {
        dependsOn(verifyNoVendoredStrudelInProduction)
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

dependencies {
    kspCommonMainMetadata(project(":klangscript-ksp"))
}
