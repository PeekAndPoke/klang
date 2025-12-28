plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "klang-engine"

include(
    // Public entry point
    ":klang",

    // Audio engine parts
    ":audio_fe",
    ":audio_be",
    ":audio_bridge",

    // utils
    ":tones",

    // Sequencers
    ":strudel",
)
