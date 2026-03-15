plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "klang-engine"

include(
    // Public entry point
    ":klang",
    // Scripting language, parser, interpreter, typesystem
    ":klangscript",
    // Visual block editor
    ":klangblocks",
    // Shared UI utilities (kraft + klangscript, JS-only)
    ":klangui",
    // External JS declarations (CodeMirror, etc.)
    ":klangjs",
    // CodeMirror editor for klangscript (JS-only)
    ":klangscript-ui",

    // Common
    ":common",

    // Audio engine parts
    ":audio_fe",
    ":audio_be",
    ":audio_bridge",
    ":audio_jsworklet",

    // utils
    ":tones",

    // Sequencers
    ":strudel",
    ":strudel-ksp",

    // Interactive/Notebook support
    ":klang-notebook",
)
