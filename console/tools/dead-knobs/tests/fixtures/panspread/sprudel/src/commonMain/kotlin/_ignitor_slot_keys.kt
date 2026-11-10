package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.IgnitorDsl

internal object IgnitorSlotKeys {
    private val s = IgnitorDsl.Slots

    private fun name(slot: IgnitorDsl): String = (slot as IgnitorDsl.Param).name

    val spread: String = name(s.spread)
    val voices: String = name(s.voices)
}
