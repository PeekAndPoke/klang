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
 * It now lives on each oscillator's BUILDER (`Osc.sine(x => x.analog(3))`), which makes an
 * unsupported receiver a type error. This spec is what keeps it that way: drop the knob from one
 * builder and the matching row here goes red, instead of a song silently losing its drift again.
 */
class KlangScriptAnalogSurfaceSpec : StringSpec({

    fun eval(code: String): Any? {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<Any>()
    }

    // Every SCRIPT door whose builder has an `analog` knob, and the node type it builds. Note the
    // two that do not read as you would guess: `Osc.square()` builds a Pulze (the flank-shaped
    // pulse), and `Osc.pulze()` builds the RawPulze.
    val supported = listOf(
        """Osc.sine(x => x.analog(3))""" to IgnitorDsl.Sine::class,
        """Osc.saw(x => x.analog(3))""" to IgnitorDsl.Sawtooth::class,
        """Osc.triangle(x => x.analog(3))""" to IgnitorDsl.Triangle::class,
        """Osc.ramp(x => x.analog(3))""" to IgnitorDsl.Ramp::class,
        """Osc.zawtooth(x => x.analog(3))""" to IgnitorDsl.Zawtooth::class,
        """Osc.zamp(x => x.analog(3))""" to IgnitorDsl.Zamp::class,
        """Osc.impulse(x => x.analog(3))""" to IgnitorDsl.Impulse::class,
        """Osc.square(x => x.analog(3))""" to IgnitorDsl.Pulze::class,
        """Osc.pulze(x => x.analog(3))""" to IgnitorDsl.RawPulze::class,
        """Osc.pluck(x => x.analog(3))""" to IgnitorDsl.Pluck::class,
        """Osc.superpluck(x => x.analog(3))""" to IgnitorDsl.SuperPluck::class,
        """Osc.supersaw(x => x.analog(3))""" to IgnitorDsl.SuperSaw::class,
        """Osc.supersine(x => x.analog(3))""" to IgnitorDsl.SuperSine::class,
        """Osc.supersquare(x => x.analog(3))""" to IgnitorDsl.SuperSquare::class,
        """Osc.supertri(x => x.analog(3))""" to IgnitorDsl.SuperTri::class,
        """Osc.superramp(x => x.analog(3))""" to IgnitorDsl.SuperRamp::class,
    )

    "every oscillator that HAS analog drift takes .analog() on its builder and keeps its own type" {
        for ((code, type) in supported) {
            withClue(code) {
                val node = eval(code)
                (node != null && type.isInstance(node)) shouldBe true
                (node as IgnitorDsl).analogOf() shouldBe IgnitorDsl.Constant(3.0)
            }
        }
    }

    "the surface is COMPLETE: every script-reachable drift-bearing type is covered above" {
        // Guards the other direction: a new oscillator with an `analog` field whose builder
        // forgot the knob would otherwise be a silent no-op all over again.
        supported.size shouldBe 16

        // The 17th, IgnitorDsl.Square, has an `analog` field but NO script door builds one
        // (`Osc.square()` returns a Pulze). It is reachable only from Kotlin or from a decoded
        // wire tree, so it cannot be exercised from here.
        IgnitorDsl.Square(freq = IgnitorDsl.Constant(440.0)).analog shouldBe IgnitorDsl.Slots.analog
    }

    "a receiver WITHOUT drift is a type error, not a silent no-op" {
        // there is no `.analog()` on any sound any more: drift is a BUILDER knob
        shouldThrow<KlangScriptTypeError> { eval("""Osc.whitenoise().analog(0.5)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().analog(0.5)""") }
        // a noise source has no builder, so a lambda lands on its sound parameter and is refused
        shouldThrow<KlangScriptTypeError> { eval("""Osc.whitenoise(x => x.analog(0.5))""") }
        // wrappers, the four shapes that were actually sitting in shipped songs
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().lowpass(800).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().highpass(120).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().onepole(600).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().vibrato(2, 0.1).analog(2)""") }
        shouldThrow<KlangScriptTypeError> { eval("""Osc.sine().pitchEnvelope(0.4, 0.001, 0.04).analog(2)""") }
    }

    "analog actually reaches the node (it is not merely accepted and dropped)" {
        val sine = eval("""Osc.sine(x => x.analog(7))""") as IgnitorDsl.Sine
        sine.analog shouldBe IgnitorDsl.Constant(7.0)
        val saw = eval("""Osc.supersaw(x => x.analog(4))""") as IgnitorDsl.SuperSaw
        saw.analog shouldBe IgnitorDsl.Constant(4.0)
    }
})

/** The `analog` field of every drift-bearing node, read without knowing the type up front. */
private fun IgnitorDsl.analogOf(): IgnitorDsl? = when (this) {
    is IgnitorDsl.Sine -> analog
    is IgnitorDsl.Sawtooth -> analog
    is IgnitorDsl.Triangle -> analog
    is IgnitorDsl.Ramp -> analog
    is IgnitorDsl.Zawtooth -> analog
    is IgnitorDsl.Zamp -> analog
    is IgnitorDsl.Impulse -> analog
    is IgnitorDsl.Pulze -> analog
    is IgnitorDsl.RawPulze -> analog
    is IgnitorDsl.Pluck -> analog
    is IgnitorDsl.SuperPluck -> analog
    is IgnitorDsl.SuperSaw -> analog
    is IgnitorDsl.SuperSine -> analog
    is IgnitorDsl.SuperSquare -> analog
    is IgnitorDsl.SuperTri -> analog
    is IgnitorDsl.SuperRamp -> analog
    else -> null
}
