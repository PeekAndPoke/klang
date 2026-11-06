/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * The flat Ignitor slot keys the sprudel doors write (`sndSuperSaw(voices)`, `sndDust(density, tail)`, `analog`,
 * `pregain`, ...), read ONCE from the `Param` objects of [IgnitorDsl.Slots] (never retyped), as the classic tail's
 * keys are in `_classic_slot_params.kt`. A rename on the Ignitor side then reaches every door; a typed literal would
 * disconnect it silently (KLANG-00417 A3).
 *
 * An object, so the names are read on first use, after `IgnitorDsl.Slots` (another module) is built.
 */
internal object IgnitorSlotKeys {
    private val s = IgnitorDsl.Slots

    private fun name(slot: IgnitorDsl): String = (slot as IgnitorDsl.Param).name

    val analog: String = name(s.analog)
    val brightness: String = name(s.brightness)
    val chaos: String = name(s.chaos)
    val color: String = name(s.color)
    val density: String = name(s.density)
    val duty: String = name(s.duty)
    val feedback: String = name(s.feedback)
    val leak: String = name(s.leak)
    val onepole: String = name(s.onepole)
    val pickPosition: String = name(s.pickPosition)
    val pregain: String = name(s.pregain)
    val spread: String = name(s.spread)
    val stiffness: String = name(s.stiffness)
    val tail: String = name(s.tail)
    val voices: String = name(s.voices)
}
