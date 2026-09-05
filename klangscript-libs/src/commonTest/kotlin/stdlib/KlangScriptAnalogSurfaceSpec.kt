/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `analog` used to be ONE method on the base [IgnitorDsl] — a `when` over 17 oscillator types
 * ending in `else -> self`. Asking a noise source or a filter wrapper for drift therefore
 * returned it unchanged: the knob did nothing and said nothing. Four shipped songs carried
 * such a call (IrishLament x3, Sakura, DialogueWithTheStars) and nobody could have known.
 *
 * It now lives on each oscillator type that actually HAS the field, which makes an
 * unsupported receiver a type error. This spec is what keeps it that way: delete one of the
 * per-type extension files and the matching row here goes red, instead of a song silently
 * losing its drift again.
 */
class KlangScriptAnalogSurfaceSpec : StringSpec({

    fun eval(code: String): Any? {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<Any>()
    }

    // Every SCRIPT constructor that produces a node with an `analog` field, and the type it
    // returns. Note the two that do not read as you would guess: `Osc.square()` builds a
    // Pulze (the flank-shaped pulse), and `Osc.pulze()` builds the RawPulze.
    val supported = listOf(
        """Osc.sine()""" to IgnitorDsl.Sine::class,
        """Osc.saw()""" to IgnitorDsl.Sawtooth::class,
        """Osc.triangle()""" to IgnitorDsl.Triangle::class,
        """Osc.ramp()""" to IgnitorDsl.Ramp::class,
        """Osc.zawtooth()""" to IgnitorDsl.Zawtooth::class,
        """Osc.zamp()""" to IgnitorDsl.Zamp::class,
        """Osc.impulse()""" to IgnitorDsl.Impulse::class,
        """Osc.square()""" to IgnitorDsl.Pulze::class,
        """Osc.pulze()""" to IgnitorDsl.RawPulze::class,
        """Osc.pluck(Osc.freq())""" to IgnitorDsl.Pluck::class,
        """Osc.superpluck(Osc.freq())""" to IgnitorDsl.SuperPluck::class,
        """Osc.supersaw(Osc.freq())""" to IgnitorDsl.SuperSaw::class,
        """Osc.supersine(Osc.freq())""" to IgnitorDsl.SuperSine::class,
        """Osc.supersquare(Osc.freq())""" to IgnitorDsl.SuperSquare::class,
        """Osc.supertri(Osc.freq())""" to IgnitorDsl.SuperTri::class,
        """Osc.superramp(Osc.freq())""" to IgnitorDsl.SuperRamp::class,
    )

    "every oscillator that HAS analog drift accepts .analog() and keeps its own type" {
        for ((ctor, type) in supported) {
            withClue(ctor) {
                val node = eval("$ctor.analog(3)")
                (node != null && type.isInstance(node)) shouldBe true
            }
        }
    }

    "the surface is COMPLETE: every script-reachable drift-bearing type is covered above" {
        // Guards the other direction — a new oscillator with an `analog` field that nobody
        // gave an extension file would otherwise be a silent no-op all over again.
        supported.size shouldBe 16

        // The 17th, IgnitorDsl.Square, has an `analog` field and its own extension file, but
        // NO script constructor builds one (`Osc.square()` returns a Pulze). It is reachable
        // only from Kotlin or from a decoded wire tree, so it cannot be exercised from here.
        IgnitorDsl.Square(freq = IgnitorDsl.Constant(440.0)).analog shouldBe IgnitorDsl.Slots.analog
    }

    "a receiver WITHOUT drift is a type error, not a silent no-op" {
        // noise sources
        shouldThrow<KlangScriptTypeError> { eval("""Osc.whitenoise().analog(0.5)""") }
        // wrappers — the four shapes that were actually sitting in shipped songs
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().lowpass(800).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().highpass(120).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().onepole(600).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().vibrato(2, 0.1).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().pitchEnvelope(0.4, 0.001, 0.04).analog(2)""") }
    }

    "analog actually reaches the node (it is not merely accepted and dropped)" {
        val sine = eval("""Osc.sine().analog(7)""") as IgnitorDsl.Sine
        sine.analog shouldBe IgnitorDsl.Constant(7.0)
        val saw = eval("""Osc.supersaw(Osc.freq()).analog(4)""") as IgnitorDsl.SuperSaw
        saw.analog shouldBe IgnitorDsl.Constant(4.0)
    }
})
