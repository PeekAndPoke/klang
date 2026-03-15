@file:Suppress("PropertyName")

plugins {
    kotlin("multiplatform")
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
                api(Deps.Npm { codemirrorLint() })
                api(Deps.Npm { lezerHighlight() })
            }
        }
    }
}
