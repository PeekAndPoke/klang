@file:Suppress("PropertyName")

// A small app that shows the visualizations alone (KLANG-00446): it depends on klangviz only, so a change there is
// built and seen in seconds, without the whole of Klang. It plays a recording and its recorded events (git-ignored,
// see src/jsMain/resources/recording/README.md). No tests: what it shows is klangviz's, tested there.

plugins {
    kotlin("multiplatform")
}

val GROUP = project.property("GROUP") as String
val VERSION_NAME = project.property("VERSION_NAME") as String

group = GROUP
version = VERSION_NAME

kotlin {
    js {
        browser {
            commonWebpackConfig { devtool = "source-map" }
            binaries.executable()
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":klangviz"))
            }
        }
    }
}

// The figures are klangviz's resources: the page fetches them, so they are laid beside it.
tasks.named<Copy>("jsProcessResources") {
    from(project(":klangviz").file("src/jsMain/resources"))
}
