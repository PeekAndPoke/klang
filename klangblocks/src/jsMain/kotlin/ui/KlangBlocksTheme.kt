package io.peekandpoke.klang.blocks.ui

internal fun categoryColour(category: String?): String = when (category) {
    "synthesis" -> "#4a6fa5"
    "sample" -> "#3a8a4a"
    "effects" -> "#3a7a3a"
    "tempo" -> "#8a7a20"
    "structural" -> "#7a3a8a"
    "random" -> "#8a3a20"
    "tonal" -> "#4a3a8a"
    "continuous" -> "#2a7a7a"
    "filters" -> "#2a6a3a"
    else -> "#555"
}
