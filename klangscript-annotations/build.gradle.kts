@file:Suppress("PropertyName")

plugins {
    kotlin("multiplatform")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser()
    }

    jvmToolchain(Deps.jvmTargetVersion)

    jvm {
    }

    sourceSets {
        commonMain {
            dependencies {
            }
        }
    }
}
