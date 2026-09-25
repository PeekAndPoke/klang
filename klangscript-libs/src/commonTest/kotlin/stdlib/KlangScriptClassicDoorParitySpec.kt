/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.getParamSlots
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `classic()` on both doors (phase 3 step 5): the script `x.classic()` and the Kotlin
 * `IgnitorDsl.classic()` build the SAME tree, and every classic slot the script reaches as
 * `OscSlot.<group>.<param>` (and `Osc.slot.<group>.<param>`) is the SAME object as the Kotlin
 * `IgnitorDsl.Slots.<group>.<param>`. The render half of the parity, every slot written through the bag,
 * is `ClassicDoorRenderParitySpec` in sprudel, the module that has both the script engine and the renderer.
 */
class KlangScriptClassicDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val s = IgnitorDsl.Slots

    /** Every classic slot: its script path and the Kotlin object it must be. */
    val slots: List<Pair<String, IgnitorDsl>> = listOf(
        "crush.amount" to s.crush.amount,
        "coarse.amount" to s.coarse.amount,
        "distort.amount" to s.distort.amount,
        "distort.shape" to s.distort.shape,
        "distort.oversample" to s.distort.oversample,
        "hpf.freq" to s.hpf.freq, "hpf.q" to s.hpf.q, "hpf.passes" to s.hpf.passes, "hpf.env" to s.hpf.env,
        "hpf.attack" to s.hpf.attack, "hpf.decay" to s.hpf.decay, "hpf.sustain" to s.hpf.sustain, "hpf.release" to s.hpf.release,
        "bpf.freq" to s.bpf.freq, "bpf.q" to s.bpf.q, "bpf.env" to s.bpf.env,
        "bpf.attack" to s.bpf.attack, "bpf.decay" to s.bpf.decay, "bpf.sustain" to s.bpf.sustain, "bpf.release" to s.bpf.release,
        "notch.freq" to s.notch.freq, "notch.q" to s.notch.q, "notch.env" to s.notch.env,
        "notch.attack" to s.notch.attack, "notch.decay" to s.notch.decay, "notch.sustain" to s.notch.sustain, "notch.release" to s.notch.release,
        "lpf.freq" to s.lpf.freq, "lpf.q" to s.lpf.q, "lpf.passes" to s.lpf.passes, "lpf.env" to s.lpf.env,
        "lpf.attack" to s.lpf.attack, "lpf.decay" to s.lpf.decay, "lpf.sustain" to s.lpf.sustain, "lpf.release" to s.lpf.release,
        "tremolo.depth" to s.tremolo.depth, "tremolo.sync" to s.tremolo.sync, "tremolo.shape" to s.tremolo.shape,
        "tremolo.skew" to s.tremolo.skew, "tremolo.phase" to s.tremolo.phase,
        "adsr.attack" to s.adsr.attack, "adsr.decay" to s.adsr.decay, "adsr.sustain" to s.adsr.sustain,
        "adsr.release" to s.adsr.release, "adsr.on" to s.adsr.on,
        "adsrCurves.attack" to s.adsrCurves.attack, "adsrCurves.decay" to s.adsrCurves.decay, "adsrCurves.release" to s.adsrCurves.release,
        "hpfCurves.attack" to s.hpfCurves.attack, "hpfCurves.decay" to s.hpfCurves.decay, "hpfCurves.release" to s.hpfCurves.release,
        "bpfCurves.attack" to s.bpfCurves.attack, "bpfCurves.decay" to s.bpfCurves.decay, "bpfCurves.release" to s.bpfCurves.release,
        "notchCurves.attack" to s.notchCurves.attack, "notchCurves.decay" to s.notchCurves.decay, "notchCurves.release" to s.notchCurves.release,
        "lpfCurves.attack" to s.lpfCurves.attack, "lpfCurves.decay" to s.lpfCurves.decay, "lpfCurves.release" to s.lpfCurves.release,
    )

    "the script door builds the Kotlin door's tree, on a bare source" {
        ks("Osc.saw().classic()") shouldBe IgnitorDsl.Sawtooth().classic()
        ks("Osc.sine().classic()") shouldBe IgnitorDsl.Sine().classic()
    }

    "the script door builds the Kotlin door's tree, on an authored instrument" {
        ks("Osc.saw().distort(0.4, \"tube\").lowpass(2500, 1.2).classic()") shouldBe
            IgnitorDsl.Sawtooth().distort(0.4, "tube").lowpass(2500.0, 1.2).classic()
    }

    "every OscSlot group property is the Kotlin slot object itself, and it names itself <group>.<param>" {
        for ((path, kotlin) in slots) {
            withClue("OscSlot.$path") {
                ks("OscSlot.$path") shouldBeSameInstanceAs kotlin
                ks("Osc.slot.$path") shouldBeSameInstanceAs kotlin
                (kotlin as IgnitorDsl.Param).name shouldBe path
            }
        }
    }

    "the list above is every slot classic() places: nothing unexposed, nothing extra" {
        val placed = IgnitorDsl.Silence.classic().getParamSlots().map { it.name }.filter { it != "analog" }.toSet()

        placed shouldBe slots.map { it.first }.toSet()
    }

    "a script tail of its own places the same slots, so the same doors fill it" {
        ks("Osc.saw().highpass(OscSlot.hpf.freq, OscSlot.hpf.q).lowpass(OscSlot.lpf.freq)") shouldBe
            IgnitorDsl.Sawtooth().highpass(s.hpf.freq, s.hpf.q).lowpass(s.lpf.freq)
    }
})
