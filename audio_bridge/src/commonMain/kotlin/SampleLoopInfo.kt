package io.peekandpoke.klang.audio_bridge

data class SampleLoopInfo(
    val start: Int,
    val end: Int,
    val anchor: Double,
    val adsr: Boolean,
)
