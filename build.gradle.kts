// The build as a whole: what every project shares. The apps are projects of their own under apps/ (KLANG-00465): the
// studio (:apps:studio, the root project until 2026-11-18), notstrom (:apps:notstrom) and what both use
// (:apps:common). This file builds nothing itself.

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    idea
    // Declared here with their versions, applied by the projects that use them: the serialization plugin by the
    // modules with stored or sent data classes, KSP and Kotest by the modules with tests (Kotest's spec discovery on
    // JS runs through KSP, KLANG-00343)
    kotlin("plugin.serialization") version Deps.kotlinVersion apply false
    id("com.google.devtools.ksp") version Deps.Ksp.version apply false
    id("io.kotest") version Deps.Test.kotest_plugin_version apply false
}

allprojects {
    apply(plugin = "idea")

    idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    repositories {
        mavenCentral()
        // KotlinX
        // maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
        // Snapshots
        // maven("https://oss.sonatype.org/content/repositories/snapshots")
        // Local
        mavenLocal()
    }
}

// The agents' build outputs (MC-00031: user claude builds into .build-claude/, its Gradle cache in .gradle-claude/)
// and the scratch folder tmp/ stay out of IntelliJ's index, where the build outputs would be indexed as a second copy
// of every build (the maintainer, 2026-10-26: "it goes crazy on these"). Set here, not by hand in the IDE, so a Gradle
// sync keeps it (as spross does).
idea {
    module {
        excludeDirs.add(file(".claude"))
        excludeDirs.add(file(".build-claude"))
        excludeDirs.add(file(".gradle-claude"))
        excludeDirs.add(file(".gradle"))
        excludeDirs.add(file(".kotlin"))
        excludeDirs.add(file("buildSrc/.build-claude"))
        excludeDirs.add(file("buildSrc/.gradle"))
        excludeDirs.add(file("tmp"))
    }
}
