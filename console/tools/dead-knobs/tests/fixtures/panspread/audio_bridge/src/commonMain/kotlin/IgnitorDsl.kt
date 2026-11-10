package io.peekandpoke.klang.audio_bridge

sealed interface IgnitorDsl {
    data class Param(val name: String, val default: Double, val description: String? = null) : IgnitorDsl

    object Slots {
        val analog: IgnitorDsl = Param(name = "analog", default = 0.0)
        val spread: IgnitorDsl = Param(name = "spread", default = 0.2)
        val voices: IgnitorDsl = Param(name = "voices", default = 8.0)
    }

    data class SuperSaw(
        val voices: IgnitorDsl = Slots.voices,
        val spread: IgnitorDsl = Slots.spread,
        val analog: IgnitorDsl = Slots.analog,
    ) : IgnitorDsl
}
