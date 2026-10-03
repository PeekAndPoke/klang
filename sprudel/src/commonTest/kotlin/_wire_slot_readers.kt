/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.VoiceData

/** TEST ONLY. A filter's cutoff envelope, read back from its slot keys by [wireFilters]. */
data class WireFilterEnv(
    val attack: Double? = null,
    val decay: Double? = null,
    val sustain: Double? = null,
    val release: Double? = null,
    val depth: Double? = null,
    val attackCurve: AdsrCurve? = null,
    val decayCurve: AdsrCurve? = null,
    val releaseCurve: AdsrCurve? = null,
)

/**
 * TEST ONLY. One of `classic()`'s four voice filters, read back from its slot keys by [wireFilters]. The typed
 * `FilterDef` voice variants this shape mirrors left the bridge with the `VoiceData.filters` field (phase 3 step 9).
 */
sealed interface WireFilter {
    data class LowPass(val freq: Double, val q: Double?, val envelope: WireFilterEnv? = null, val passes: Int = 1) : WireFilter
    data class HighPass(val freq: Double, val q: Double?, val envelope: WireFilterEnv? = null, val passes: Int = 1) : WireFilter
    data class BandPass(val freq: Double, val q: Double?, val envelope: WireFilterEnv? = null) : WireFilter
    data class Notch(val freq: Double, val q: Double?, val envelope: WireFilterEnv? = null) : WireFilter
}

/**
 * The four voice filters of a wire [VoiceData], read back from their `classic()` slot keys (phase 3 step 8 sends
 * them as slots) into the [WireFilter] shape the filter specs assert on, in `classic()`'s order: highpass, bandpass,
 * notch, lowpass. A kind is present when its `<door>.freq` key is; its envelope when any of `<door>.env` and the four
 * stage keys is.
 *
 * The keys are LITERALS on purpose: this is the test side's independent statement of the slot names, so a door
 * writing a wrong key fails here instead of agreeing with itself.
 */
fun VoiceData.wireFilters(): List<WireFilter> {
    val slots = ignitorParams ?: emptyMap()

    fun curve(door: String, stage: String): AdsrCurve? =
        slots["${door}Curves.$stage"]?.let { AdsrCurve.entries[it.toInt()] }

    fun envelope(door: String): WireFilterEnv? {
        val keys = listOf("env", "attack", "decay", "sustain", "release")

        if (keys.none { slots.containsKey("$door.$it") }) {
            return null
        }

        return WireFilterEnv(
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
            add(WireFilter.HighPass(it, slots["hpf.q"], envelope("hpf"), passes = slots["hpf.passes"]?.toInt() ?: 1))
        }
        slots["bpf.freq"]?.let { add(WireFilter.BandPass(it, slots["bpf.q"], envelope("bpf"))) }
        slots["notch.freq"]?.let { add(WireFilter.Notch(it, slots["notch.q"], envelope("notch"))) }
        slots["lpf.freq"]?.let {
            add(WireFilter.LowPass(it, slots["lpf.q"], envelope("lpf"), passes = slots["lpf.passes"]?.toInt() ?: 1))
        }
    }

    return out
}

/** TEST ONLY. The first filter of kind [T], or null. */
inline fun <reified T : WireFilter> List<WireFilter>.getByType(): T? = filterIsInstance<T>().firstOrNull()
