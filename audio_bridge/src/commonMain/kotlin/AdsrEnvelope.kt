package io.peekandpoke.klang.audio_bridge

data class AdsrEnvelope(
    val attack: Double,
    val decay: Double,
    val sustain: Double,
    val release: Double,
)
