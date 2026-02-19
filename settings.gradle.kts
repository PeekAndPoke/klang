plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "klang-engine"

include(
    // Public entry point
    ":klang",

    // Common
    ":common",

    // Audio engine parts
    ":audio_fe",
    ":audio_be",
    ":audio_bridge",
    ":audio_jsworklet",

    // utils
    ":klangscript",
    ":tones",

    // Sequencers
    ":strudel",
    ":strudel-ksp",

    // Interactive/Notebook support
    ":klang-notebook",
)
