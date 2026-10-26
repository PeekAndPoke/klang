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
        browser {
            commonWebpackConfig { devtool = "source-map" }
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                // CodeMirror 6
                api(Deps.Npm { codemirrorState() })
                api(Deps.Npm { codemirrorView() })
                api(Deps.Npm { codemirrorCommands() })
                api(Deps.Npm { codemirrorLanguage() })
                api(Deps.Npm { codemirrorLangJavascript() })
                api(Deps.Npm { codemirrorBasicSetup() })
                api(Deps.Npm { codemirrorAutocomplete() })
                api(Deps.Npm { codemirrorLint() })
                api(Deps.Npm { codemirrorSearch() })
                api(Deps.Npm { codemirrorMinimap() })
                api(Deps.Npm { lezerHighlight() })
            }
        }
    }
}
