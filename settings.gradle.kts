plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "klang"

include(
    // Audio engine
    ":audio_engine",
    ":audio_fe",
    ":audio_be",
    ":audio_bridge",

    // Sequencers
    ":strudel",
)
