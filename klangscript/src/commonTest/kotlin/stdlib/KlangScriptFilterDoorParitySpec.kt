/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * Dual-surface rule: every DSL must be usable from Kotlin directly, with the same parameters
 * meaning the same things. The filter doors drifted — the script door took an `IgnitorDsl` for
 * every knob plus `analog`, while the Kotlin door was `Double`-only and had no `analog` at all.
 * Sakura's `.lowpass(freq = Osc.sine(0.3)..., analog = Osc.slot.analog)` was therefore
 * expressible in a song and NOT from Kotlin.
 *
 * This spec compares the two doors node-for-node. It deliberately compares the FILTER node's
 * own fields rather than whole trees, so a difference in how the two sides build the upstream
 * oscillator cannot mask (or fake) a door difference.
 */
class KlangScriptFilterDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    val modulated = IgnitorDsl.Param("cut", 800.0)
    val q = IgnitorDsl.Constant(1.2)
    val analog = IgnitorDsl.Constant(4.0)

    "lowpass: a modulated freq + analog is expressible on BOTH doors, identically" {
        val script = ks("""Osc.saw().lowpass(freq = Osc.param("cut", 800), q = 1.2, passes = 2, analog = 4)""")
            as IgnitorDsl.Lowpass
        val kotlin = IgnitorDsl.Sawtooth().lowpass(modulated, q, passes = 2, analog = analog)

        withClue("freq") { script.freq shouldBe kotlin.freq }
        withClue("q") { script.q shouldBe kotlin.q }
        withClue("analog") { script.analog shouldBe kotlin.analog }
        withClue("passes") { script.passes shouldBe kotlin.passes }
    }

    "highpass: same" {
        val script = ks("""Osc.saw().highpass(freq = Osc.param("cut", 800), q = 1.2, passes = 3, analog = 4)""")
            as IgnitorDsl.Highpass
        val kotlin = IgnitorDsl.Sawtooth().highpass(modulated, q, passes = 3, analog = analog)
        script.freq shouldBe kotlin.freq
        script.q shouldBe kotlin.q
        script.analog shouldBe kotlin.analog
        script.passes shouldBe kotlin.passes
    }

    "bandpass and notch: analog reaches the node from Kotlin too (it had no way in before)" {
        val bp = ks("""Osc.saw().bandpass(freq = Osc.param("cut", 800), q = 1.2, analog = 4)""")
            as IgnitorDsl.Bandpass
        val bpk = IgnitorDsl.Sawtooth().bandpass(modulated, q, analog)
        bp.freq shouldBe bpk.freq
        bp.q shouldBe bpk.q
        bp.analog shouldBe bpk.analog

        val nt = ks("""Osc.saw().notch(freq = Osc.param("cut", 800), q = 1.2, analog = 4)""")
            as IgnitorDsl.Notch
        val ntk = IgnitorDsl.Sawtooth().notch(modulated, q, analog)
        nt.freq shouldBe ntk.freq
        nt.q shouldBe ntk.q
        nt.analog shouldBe ntk.analog
    }

    "onepole: a modulated freq works from Kotlin (it was Double-only)" {
        val script = ks("""Osc.saw().onepole(Osc.param("cut", 800))""") as IgnitorDsl.OnePoleLowpass
        val kotlin = IgnitorDsl.Sawtooth().onepole(modulated)
        script.freq shouldBe kotlin.freq
    }

    "the scalar overloads still wrap in Constant, and the defaults did not move" {
        // Adding `analog` must not change what an existing Kotlin caller builds.
        val lp = IgnitorDsl.Sawtooth().lowpass(800.0)
        lp.freq shouldBe IgnitorDsl.Constant(800.0)
        lp.q shouldBe IgnitorDsl.Constant(0.707)
        lp.analog shouldBe IgnitorDsl.Constant(0.0)
        lp.passes shouldBe 1

        // ...and the scalar door reaches analog too, in the same fourth slot as the script door.
        IgnitorDsl.Sawtooth().lowpass(800.0, 1.0, 2, 3.0).analog shouldBe IgnitorDsl.Constant(3.0)
        IgnitorDsl.Sawtooth().notch(1000.0, 0.9, 2.0).analog shouldBe IgnitorDsl.Constant(2.0)
    }
})
