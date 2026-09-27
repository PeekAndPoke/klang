/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * The four voice filters of a wire [VoiceData], read back from their `classic()` slot keys (phase 3 step 8 sends
 * them as slots, not as `FilterDef`s) into the `FilterDef` shape the filter specs assert on, in `classic()`'s order:
 * highpass, bandpass, notch, lowpass. A kind is present when its `<door>.freq` key is; its envelope when any of
 * `<door>.env` and the four stage keys is.
 *
 * The keys are LITERALS on purpose: this is the test side's independent statement of the slot names, so a door
 * writing a wrong key fails here instead of agreeing with itself.
 */
fun VoiceData.wireFilters(): FilterDefs {
    val slots = oscParams ?: emptyMap()

    fun curve(door: String, stage: String): AdsrCurve? =
        slots["${door}Curves.$stage"]?.let { AdsrCurve.entries[it.toInt()] }

    fun envelope(door: String): FilterEnvDef? {
        val keys = listOf("env", "attack", "decay", "sustain", "release")

        if (keys.none { slots.containsKey("$door.$it") }) {
            return null
        }

        return FilterEnvDef(
            attack = slots["$door.attack"],
            decay = slots["$door.decay"],
            sustain = slots["$door.sustain"],
            release = slots["$door.release"],
            depth = slots["$door.env"],
            attackCurve = curve(door, "attack"),
            decayCurve = curve(door, "decay"),
            releaseCurve = curve(door, "release"),
        )
    }

    val out = buildList {
        slots["hpf.freq"]?.let {
            add(FilterDef.HighPass(it, slots["hpf.q"], envelope("hpf"), passes = slots["hpf.passes"]?.toInt() ?: 1))
        }
        slots["bpf.freq"]?.let { add(FilterDef.BandPass(it, slots["bpf.q"], envelope("bpf"))) }
        slots["notch.freq"]?.let { add(FilterDef.Notch(it, slots["notch.q"], envelope("notch"))) }
        slots["lpf.freq"]?.let {
            add(FilterDef.LowPass(it, slots["lpf.q"], envelope("lpf"), passes = slots["lpf.passes"]?.toInt() ?: 1))
        }
    }

    return FilterDefs(out)
}
