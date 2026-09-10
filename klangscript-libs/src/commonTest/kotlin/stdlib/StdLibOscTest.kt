/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Integration tests for the Osc DSL in KlangScript.
 *
 * Validates that KlangScript code builds correct IgnitorDsl trees
 * through the full engine pipeline (parse → interpret → native interop).
 */
class StdLibOscTest : StringSpec({

    fun evalIgnitorDsl(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        val value = result.value
        value.shouldBeInstanceOf<IgnitorDsl>()
        return value
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Factory methods
    // ═════════════════════════════════════════════════════════════════════════════

    "Osc.sine() returns IgnitorDsl.Sine" {
        val dsl = evalIgnitorDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    // ── ADSR knobs: declickSeconds + expK (opt-in, wrap-or-copy onto an Adsr) ─────────
    "Osc.saw().declickSeconds(0.001) wraps an Adsr with the declickSeconds slot set" {
        val dsl = evalIgnitorDsl("Osc.saw().declickSeconds(0.001)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        dsl.declickSeconds shouldBe IgnitorDsl.Constant(0.001)
    }

    "Osc.saw().adsr(...).expK(4.0) sets the expK slot on the Adsr node" {
        val dsl = evalIgnitorDsl("Osc.saw().adsr(0.01, 0.1, 0.5, 0.2).expK(4.0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        dsl.expK shouldBe IgnitorDsl.Constant(4.0)
    }

    "Osc.sine(5) returns Sine with Constant freq" {
        val dsl = evalIgnitorDsl("Osc.sine(5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 5.0
    }

    "Osc.sine() with no args uses Freq for freq (= voice frequency)" {
        val dsl = evalIgnitorDsl("Osc.sine()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq shouldBe IgnitorDsl.Freq
    }

    "Osc.freq() returns IgnitorDsl.Freq" {
        val dsl = evalIgnitorDsl("Osc.freq()")
        dsl shouldBe IgnitorDsl.Freq
    }

    "Osc.sine(Osc.freq().div(2)) produces Sine with halved Freq" {
        val dsl = evalIgnitorDsl("Osc.sine(Osc.freq().div(2))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq shouldBe IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0))
    }

    "Osc.sine(Osc.freq().mul(2)) produces Sine with doubled Freq" {
        val dsl = evalIgnitorDsl("Osc.sine(Osc.freq().mul(2))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq shouldBe IgnitorDsl.Times(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0))
    }

    "Osc.saw() returns Sawtooth" {
        evalIgnitorDsl("Osc.saw()").shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "Osc.square() returns Pulze (square/pulse/pulze are one pulse oscillator)" {
        evalIgnitorDsl("Osc.square()").shouldBeInstanceOf<IgnitorDsl.Pulze>()
    }

    "Osc.supersaw() returns SuperSaw" {
        evalIgnitorDsl("Osc.supersaw()").shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
    }

    "Osc.supersaw(10) returns SuperSaw with Constant freq" {
        val dsl = evalIgnitorDsl("Osc.supersaw(10)")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 10.0
    }

    "Osc.supersaw() has correct defaults" {
        val dsl = evalIgnitorDsl("Osc.supersaw()")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.freq shouldBe IgnitorDsl.Freq
        // Defaults come from IgnitorDsl.Slots (overridable Params), matching the backend `.sound("supersaw")`.
        dsl.voices shouldBe IgnitorDsl.Slots.voices
        dsl.spread shouldBe IgnitorDsl.Slots.spread
        dsl.analog shouldBe IgnitorDsl.Slots.analog
    }

    "Osc.supersaw(440) backward compat — freq only" {
        val dsl = evalIgnitorDsl("Osc.supersaw(440)")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.freq shouldBe IgnitorDsl.Constant(440.0)
        dsl.voices shouldBe IgnitorDsl.Slots.voices
        dsl.spread shouldBe IgnitorDsl.Slots.spread
        dsl.analog shouldBe IgnitorDsl.Slots.analog
    }

    "Osc.supersaw with voices=1 — degenerate single voice" {
        val dsl = evalIgnitorDsl("Osc.supersaw(x => x.voices(1))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.voices shouldBe IgnitorDsl.Constant(1.0)
    }

    // Knobs live on the builder handed to the configure lambda.
    "Osc.supersaw with all params" {
        val dsl = evalIgnitorDsl("Osc.supersaw(x => x.voices(4).spread(0.1).analog(0.3))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.voices shouldBe IgnitorDsl.Constant(4.0)
        dsl.spread shouldBe IgnitorDsl.Constant(0.1)
        dsl.analog shouldBe IgnitorDsl.Constant(0.3)
    }

    "Osc.supersaw voices accepts IgnitorDsl" {
        val dsl = evalIgnitorDsl("Osc.supersaw(x => x.voices(Osc.sine(0.5)))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        dsl.voices.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "Osc.supersine with voices and detune" {
        val dsl = evalIgnitorDsl("Osc.supersine(x => x.voices(6).spread(0.15))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSine>()
        dsl.voices shouldBe IgnitorDsl.Constant(6.0)
        dsl.spread shouldBe IgnitorDsl.Constant(0.15)
    }

    "Osc.supersquare with voices and analog" {
        val dsl = evalIgnitorDsl("Osc.supersquare(x => x.voices(3).spread(0.2).analog(0.2))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperSquare>()
        dsl.voices shouldBe IgnitorDsl.Constant(3.0)
        dsl.analog shouldBe IgnitorDsl.Constant(0.2)
    }

    "Osc.supertri with voices" {
        val dsl = evalIgnitorDsl("Osc.supertri(x => x.voices(12))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperTri>()
        dsl.voices shouldBe IgnitorDsl.Constant(12.0)
    }

    "Osc.superramp with all params" {
        val dsl = evalIgnitorDsl("Osc.superramp(x => x.voices(5).spread(0.4).analog(0.1))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperRamp>()
        dsl.voices shouldBe IgnitorDsl.Constant(5.0)
        dsl.spread shouldBe IgnitorDsl.Constant(0.4)
        dsl.analog shouldBe IgnitorDsl.Constant(0.1)
    }

    "Osc.superpluck with voices and detune" {
        val dsl = evalIgnitorDsl("Osc.superpluck(x => x.voices(4).spread(0.05))")
        dsl.shouldBeInstanceOf<IgnitorDsl.SuperPluck>()
        dsl.voices shouldBe IgnitorDsl.Constant(4.0)
        dsl.spread shouldBe IgnitorDsl.Constant(0.05)
    }

    "Osc.whitenoise() returns WhiteNoise with flat color (0.0)" {
        val dsl = evalIgnitorDsl("Osc.whitenoise()")
        dsl.shouldBeInstanceOf<IgnitorDsl.WhiteNoise>()
        // KlangScript bakes the literal default → Constant(0.0) (the engine bypasses the tilt at 0)
        dsl.color shouldBe IgnitorDsl.Constant(0.0)
    }

    "Osc.whitenoise(color = -0.5) sets the spectral-tilt knob" {
        val dsl = evalIgnitorDsl("Osc.whitenoise(-0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.WhiteNoise>()
        dsl.color shouldBe IgnitorDsl.Constant(-0.5)
    }

    "Osc.perlin() returns PerlinNoise" {
        evalIgnitorDsl("Osc.perlin()").shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
    }

    "Osc.perlin(3) returns PerlinNoise with Constant rate" {
        val dsl = evalIgnitorDsl("Osc.perlin(3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
        dsl.rate.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.rate as IgnitorDsl.Constant).value shouldBe 3.0
    }

    "Osc.dust(0.5) returns Dust with Constant density" {
        val dsl = evalIgnitorDsl("Osc.dust(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Dust>()
        dsl.density.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.density as IgnitorDsl.Constant).value shouldBe 0.5
    }

    "Osc.dust(tail = 4, bipolar = 1) sets the heavy-tail + bipolar knobs (named args)" {
        val dsl = evalIgnitorDsl("Osc.dust(0.5, 4, 1)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Dust>()
        dsl.tail shouldBe IgnitorDsl.Constant(4.0)
        dsl.bipolar shouldBe IgnitorDsl.Constant(1.0)
    }

    "Osc.brownnoise(depth = 0.3) sets the white-leak knob" {
        val dsl = evalIgnitorDsl("Osc.brownnoise(0.3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.BrownNoise>()
        dsl.depth shouldBe IgnitorDsl.Constant(0.3)
    }

    "Osc.pluck() returns Pluck" {
        evalIgnitorDsl("Osc.pluck()").shouldBeInstanceOf<IgnitorDsl.Pluck>()
    }

    "Osc.silence() returns Silence" {
        evalIgnitorDsl("Osc.silence()") shouldBe IgnitorDsl.Silence
    }

    "Osc.constant(42) returns Constant" {
        val dsl = evalIgnitorDsl("Osc.constant(42)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Constant>()
        dsl.value shouldBe 42.0
    }

    "Osc.param creates named Param" {
        val dsl = evalIgnitorDsl("""Osc.param("cutoff", 1000, "Filter cutoff")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Param>()
        dsl.name shouldBe "cutoff"
        dsl.default shouldBe 1000.0
        dsl.description shouldBe "Filter cutoff"
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Chaining — filters, effects, envelope
    // ═════════════════════════════════════════════════════════════════════════════

    "lowpass chaining with default q" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.freq as IgnitorDsl.Constant).value shouldBe 2000.0
    }

    "lowpass chaining with explicit q" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(2000, 2.0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.q.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.q as IgnitorDsl.Constant).value shouldBe 2.0
    }

    "lowpass with IgnitorDsl cutoff (audio-rate modulation)" {
        val dsl = evalIgnitorDsl("Osc.sine().lowpass(Osc.perlin())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.PerlinNoise>()
    }

    "adsr chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().adsr(0.01, 0.1, 0.5, 0.3)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "distort chaining produces Shape(Drive(...))" {
        val dsl = evalIgnitorDsl("Osc.saw().distort(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.oversample shouldBe 0
        val drive = dsl.inner
        drive.shouldBeInstanceOf<IgnitorDsl.Drive>()
        drive.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "distort with oversample factor" {
        val dsl = evalIgnitorDsl("""Osc.saw().distort(0.8, "exp", 4)""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.shape shouldBe "exp"
        dsl.oversample shouldBe 4
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
    }

    "detune chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().detune(7)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Detune>()
    }

    "tremolo chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().tremolo(5, 0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Tremolo>()
    }

    "vibrato chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().vibrato(5, 0.02)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Vibrato>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Arithmetic
    // ═════════════════════════════════════════════════════════════════════════════

    "plus combines two ignitors" {
        val dsl = evalIgnitorDsl("Osc.sine().plus(Osc.saw())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    "plus with number creates Constant" {
        val dsl = evalIgnitorDsl("Osc.sine().plus(1)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 1.0
    }

    "mul with number creates Constant (lowers to Times)" {
        val dsl = evalIgnitorDsl("Osc.sine().mul(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Times>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 0.5
    }

    "div with number creates Constant" {
        val dsl = evalIgnitorDsl("Osc.sine().div(2)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Div>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 2.0
    }

    "minus creates Minus(left, right)" {
        val dsl = evalIgnitorDsl("Osc.sine().minus(Osc.saw())")
        dsl.shouldBeInstanceOf<IgnitorDsl.Minus>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // min / max are clamps, and the node crossing is deliberate
    //
    // "sig.max(x)" reads "sig, at most x", so it caps, and a cap is the per-sample
    // MINIMUM of the two signals. The doors therefore build the opposite-named node.
    // If a future change makes max() build IgnitorDsl.Max, these tests must fail.
    // ═════════════════════════════════════════════════════════════════════════════

    "max caps, so it builds IgnitorDsl.Min" {
        val dsl = evalIgnitorDsl("Osc.sine().max(0.8)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Min>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 0.8
    }

    "min floors, so it builds IgnitorDsl.Max" {
        val dsl = evalIgnitorDsl("Osc.sine().min(0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Max>()
        dsl.left.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.right.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.right as IgnitorDsl.Constant).value shouldBe 0.0
    }

    "min and max keep the receiver on the left" {
        val capped = evalIgnitorDsl("Osc.saw().max(Osc.sine())")
        capped.shouldBeInstanceOf<IgnitorDsl.Min>()
        capped.left.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        capped.right.shouldBeInstanceOf<IgnitorDsl.Sine>()

        val floored = evalIgnitorDsl("Osc.saw().min(Osc.sine())")
        floored.shouldBeInstanceOf<IgnitorDsl.Max>()
        floored.left.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        floored.right.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Complex compositions
    // ═════════════════════════════════════════════════════════════════════════════

    "complex composition — supersaw with LFO-modulated lowpass and envelope" {
        val dsl = evalIgnitorDsl(
            """
            Osc.supersaw().lowpass(Osc.sine(5).plus(1).times(1000).plus(1000)).adsr(0.01, 0.3, 0.5, 0.5)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<IgnitorDsl.Adsr>()
        val lowpass = dsl.inner
        lowpass.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        lowpass.inner.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
    }

    "variable assignment and reuse" {
        val dsl = evalIgnitorDsl(
            """
            let lfo = Osc.sine(5).plus(1).times(500).plus(500)
            Osc.saw().lowpass(lfo)
        """.trimIndent()
        )
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        // The LFO is a Plus(Times(Plus(Sine, Constant), Constant), Constant)
        dsl.freq.shouldBeInstanceOf<IgnitorDsl.Plus>()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Analog drift
    // ═════════════════════════════════════════════════════════════════════════════

    "analog sets drift on oscillator" {
        val dsl = evalIgnitorDsl("Osc.sine(x => x.analog(0.3))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.analog.shouldBeInstanceOf<IgnitorDsl.Constant>()
        (dsl.analog as IgnitorDsl.Constant).value shouldBe 0.3
    }

    "analog on a type without drift FAILS LOUDLY (it used to be a silent no-op)" {
        // `analog` used to be one `when` over 17 oscillator types with `else -> self`, so
        // asking a noise source or a wrapper for drift silently returned it unchanged; the
        // knob did nothing and said nothing. It now lives on each oscillator's BUILDER
        // (`Osc.sine(x => x.analog(3))`), so no sound has an `.analog()` any more and an
        // unsupported receiver is a type error.
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        shouldThrow<KlangScriptTypeError> {
            engine.execute("Osc.whitenoise().analog(0.5)")
        }
        // ...and a wrapper is equally unsupported: drift belongs to the oscillator, and by
        // the time a filter has wrapped it there is no oscillator left to configure.
        shouldThrow<KlangScriptTypeError> {
            engine.execute("Osc.sine().onepole(600).analog(2)")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch via KlangScript
    // ═════════════════════════════════════════════════════════════════════════════

    "drive chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().drive(0.5)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Drive>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "shape chaining" {
        val dsl = evalIgnitorDsl("""Osc.sine().shape("hard")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.shape shouldBe "hard"
    }

    "shape with default curve" {
        val dsl = evalIgnitorDsl("Osc.sine().shape()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.shape shouldBe "soft"
        dsl.oversample shouldBe 0
    }

    "shape with oversample factor" {
        val dsl = evalIgnitorDsl("""Osc.sine().shape("hard", 2)""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.shape shouldBe "hard"
        dsl.oversample shouldBe 2
    }

    "bandpass chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().bandpass(1000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Bandpass>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "bandpass with explicit Q" {
        val dsl = evalIgnitorDsl("Osc.sine().bandpass(1000, 5.0)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Bandpass>()
    }

    "notch chaining" {
        val dsl = evalIgnitorDsl("Osc.sine().notch(1000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Notch>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
    }

    "eq wraps the inner into an empty Eq" {
        val dsl = evalIgnitorDsl("Osc.sine().eq()")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.sections shouldBe emptyList()
    }

    "eq is idempotent, and a second eq(...) continues the first" {
        val dsl = evalIgnitorDsl("Osc.sine().eq().eq(e => e.band(1200))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.sections.size shouldBe 1
    }

    "band on the EqBuilder adds a bell with defaults" {
        val dsl = evalIgnitorDsl("Osc.sine().eq(e => e.band(1200))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Sine>()
        dsl.sections.size shouldBe 1
        val bell = dsl.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        (bell.freq as IgnitorDsl.Constant).value shouldBe 1200.0
        (bell.q as IgnitorDsl.Constant).value shouldBe 0.707
        (bell.db as IgnitorDsl.Constant).value shouldBe 0.0
    }

    "band and tap exist only on the EqBuilder, not on a sound or on the Eq node" {
        // TYPE and the method name matter: a bare shouldThrow<Exception> would also pass
        val onSound = shouldThrow<KlangScriptTypeError> { evalIgnitorDsl("Osc.sine().band(1200)") }
        onSound.message shouldContain "has no method 'band'"
        // the pre-builder chain form is gone: .eq() returns the sound, knobs live in the lambda
        val onEq = shouldThrow<KlangScriptTypeError> { evalIgnitorDsl("Osc.sine().eq().band(1200)") }
        onEq.message shouldContain "has no method 'band'"
        val tapOnSound = shouldThrow<KlangScriptTypeError> { evalIgnitorDsl("Osc.sine().tap(850)") }
        tapOnSound.message shouldContain "has no method 'tap'"
    }

    "band appends to an existing Eq in list order" {
        val dsl = evalIgnitorDsl("Osc.sine().eq(e => e.band(300, 1.0, 6).band(2500))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        dsl.sections.size shouldBe 2
        val first = dsl.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        (first.freq as IgnitorDsl.Constant).value shouldBe 300.0
        (first.db as IgnitorDsl.Constant).value shouldBe 6.0
        val second = dsl.sections[1].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        (second.freq as IgnitorDsl.Constant).value shouldBe 2500.0
    }

    "band with all-named args skips q" {
        // The escape from the positional trap (band(1200, 6) sets q, not gain). Knob defaults are
        // literals, so a named subset binds.
        val dsl = evalIgnitorDsl("Osc.saw().eq(e => e.band(freq = 1200, db = 6))")
        val bell = (dsl as IgnitorDsl.Eq).sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        (bell.q as IgnitorDsl.Constant).value shouldBe 0.707
        (bell.db as IgnitorDsl.Constant).value shouldBe 6.0
    }

    "base filters chain AFTER the eq lambda (the shape the songs ship)" {
        val dsl = evalIgnitorDsl("Osc.saw().eq(e => e.tap(850, 0.707, 1.7)).notch(210, 2.5).lowpass(5250)")
        val lowpass = dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val notch = lowpass.inner.shouldBeInstanceOf<IgnitorDsl.Notch>()
        val eq = notch.inner.shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
    }

    "tap adds a RawTap section with defaults" {
        val dsl = evalIgnitorDsl("Osc.saw().eq(e => e.tap(850))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        val tap = dsl.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
        (tap.freq as IgnitorDsl.Constant).value shouldBe 850.0
        (tap.q as IgnitorDsl.Constant).value shouldBe 0.707 // C1: unified default q
        (tap.gain as IgnitorDsl.Constant).value shouldBe 1.0
    }

    "tap and band mix in one section list, in written order" {
        val dsl = evalIgnitorDsl("Osc.saw().eq(e => e.tap(850, 0.707, 1.7).tap(2500, 0.7, 5.0).band(4000, 0.7, -3))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        dsl.sections.size shouldBe 3
        dsl.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
        dsl.sections[1].shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>()
        dsl.sections[2].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        ((dsl.sections[1] as IgnitorDsl.EqSection.RawTap).gain as IgnitorDsl.Constant).value shouldBe 5.0
    }

    "band accepts an IgnitorDsl freq (note tracking)" {
        val dsl = evalIgnitorDsl("Osc.saw().eq(e => e.band(Osc.freq().mul(2)))")
        dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        val bell = dsl.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        bell.freq.shouldBeInstanceOf<IgnitorDsl.Times>()
    }

    "drive + shape chain" {
        val dsl = evalIgnitorDsl("""Osc.saw().drive(0.3).shape("fold")""")
        dsl.shouldBeInstanceOf<IgnitorDsl.Shape>()
        dsl.shape shouldBe "fold"
        dsl.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
    }
})
