package io.peekandpoke.klang.audio_bridge

private fun knob(stage: String, knob: String, default: Double): KatalystKnobDsl.Param =
    KatalystKnobDsl.Param(name = "$stage.$knob", default = default)

class KatalystDelaySlots internal constructor() {
    val wet: KatalystKnobDsl.Param = knob(stage = "delay", knob = "wet", default = 0.0)
}
